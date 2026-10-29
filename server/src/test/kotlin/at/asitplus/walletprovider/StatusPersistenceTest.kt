package at.asitplus.walletprovider

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/** The status lists are exported to the database periodically and imported again on startup. */
class StatusPersistenceTest : ProviderTestSupport() {

    @Autowired
    lateinit var databaseService: DatabaseService

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

    @Test
    fun `statuses beyond the bit size of the list are refused`() {
        assertThrows<IllegalStateException> { InMemoryTokenStore().setStatus(1, 0uL, TokenStatus(4.toUByte())) }
    }
}
