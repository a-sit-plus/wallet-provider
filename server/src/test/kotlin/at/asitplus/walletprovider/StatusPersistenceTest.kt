package at.asitplus.walletprovider

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** The status lists are exported to the database periodically and imported again on startup. */
class StatusPersistenceTest : ProviderTestSupport() {

    @Autowired
    lateinit var databaseService: DatabaseService

    @Autowired
    lateinit var keyMaterial: KeyMaterial

    /** Not used by the running provider, so its scheduled export does not overwrite the rows of this test */
    private val period = 4711

    /** A store with 5 handed out indices, of which index 1 is invalid and index 3 suspended */
    private fun storeWithStatuses() = InMemoryTokenStore().apply {
        repeat(5) { getNextFreeIndex(period) }
        setStatus(period, 1uL, TokenStatus.Invalid)
        setStatus(period, 3uL, TokenStatus.Suspended)
    }

    @Test
    fun `key storage status lists survive a restart`() {
        val store = storeWithStatuses()
        databaseService.saveKeyStorageStatusLists(store.exportData()).getOrThrow()

        val restored = InMemoryTokenStore().apply { importData(databaseService.loadKeyStorageStatusLists().getOrThrow()) }
        assertEquals(store.getStatusListView(period), restored.getStatusListView(period))
        assertEquals(5, restored.getNextFreeIndex(period), "index counter is restored")
    }

    @Test
    fun `client status lists survive a restart`() {
        val store = storeWithStatuses()
        databaseService.saveClientStatusLists(store.exportData()).getOrThrow()

        val restored = InMemoryTokenStore().apply { importData(databaseService.loadClientStatusLists().getOrThrow()) }
        assertEquals(store.getStatusListView(period), restored.getStatusListView(period))
        assertEquals(5, restored.getNextFreeIndex(period), "index counter is restored")
    }

    @Test
    fun `status lists are written on shutdown`() {
        // No scheduled export before the shutdown
        val service = KeyStorageTokenStoreService(
            InMemoryTokenStore(), databaseService, keyMaterial, "http://127.0.0.1", exportInterval = 1.hours
        )
        val shutdownPeriod = 4712
        repeat(3) { service.tokenStore.getNextFreeIndex(shutdownPeriod) }
        service.tokenStore.setStatus(shutdownPeriod, 1uL, TokenStatus.Invalid)

        service.close()

        val (counter, view) = databaseService.loadKeyStorageStatusLists().getOrThrow().getValue(shutdownPeriod)
        assertEquals(3, counter)
        assertEquals(service.tokenStore.getStatusListView(shutdownPeriod), view)
    }

    @Test
    fun `saving again replaces the stored status list`() {
        val store = storeWithStatuses()
        databaseService.saveKeyStorageStatusLists(store.exportData()).getOrThrow()
        store.setStatus(period, 1uL, TokenStatus.Valid)
        databaseService.saveKeyStorageStatusLists(store.exportData()).getOrThrow()

        val restored = InMemoryTokenStore().apply { importData(databaseService.loadKeyStorageStatusLists().getOrThrow()) }
        assertEquals(store.getStatusListView(period), restored.getStatusListView(period))
    }
}

class InMemoryTokenStoreTest {

    @Test
    fun `indices are handed out per time period`() {
        val store = InMemoryTokenStore()
        assertEquals(listOf(0, 1, 2), List(3) { store.getNextFreeIndex(1) })
        assertEquals(0, store.getNextFreeIndex(2))
    }

    /** Runs [action] [times] times on each of [threads] threads at once, and returns the errors */
    private fun inParallel(threads: Int, times: Int, action: (thread: Int) -> Unit): List<Throwable> {
        val errors = ConcurrentLinkedQueue<Throwable>()
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { thread ->
            pool.execute { repeat(times) { runCatching { action(thread) }.onFailure { errors.add(it) } } }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(1, TimeUnit.MINUTES))
        return errors.toList()
    }

    @Test
    fun `concurrent attestations get distinct indices`() {
        val store = InMemoryTokenStore()
        val indices = ConcurrentLinkedQueue<Int>()
        val errors = inParallel(threads = 8, times = 500) { indices.add(store.getNextFreeIndex(1)) }

        assertEquals(emptyList(), errors)
        assertEquals((0 until 4000).toList(), indices.sorted())
    }

    @Test
    fun `exports while statuses change do not fail`() {
        val store = InMemoryTokenStore()
        val errors = inParallel(threads = 4, times = 500) { thread ->
            if (thread == 0) {
                store.exportData()
            } else {
                // New time periods add entries to the store while it is exported
                val period = thread * 1000 + store.getNextFreeIndex(thread) % 50
                store.setStatus(period, store.getNextFreeIndex(period).toULong(), TokenStatus.Invalid)
            }
        }

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `statuses beyond the bit size of the list are refused`() {
        assertThrows<IllegalStateException> { InMemoryTokenStore().setStatus(1, 0uL, TokenStatus(4.toUByte())) }
    }
}
