package at.asitplus.walletprovider.service.storage

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import io.github.aakira.napier.Napier
import java.util.*
import kotlin.concurrent.schedule
import kotlin.time.Duration

/**
 * Keeps the status lists in memory and writes them to the database every [exportInterval], after a change of a status
 * by the admin, and a last time on shutdown ([close], called by Spring), so that no revocation or handed out index is
 * lost when the provider is stopped.
 */
interface TokenStoreService : AutoCloseable {
    val tokenStore: InMemoryTokenStore
    val databaseService: DatabaseService
    val keyMaterial: KeyMaterial
    val statusListBaseUrl: String
    val exportInterval: Duration

    /** Runs the scheduled exports; a daemon thread, so it does not keep the JVM alive */
    val exportTimer: Timer

    fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>>
    fun saveToDatabase(data: Map<Int, Pair<Int, StatusListView>>): Result<Unit>

    fun exportToDatabase() {
        saveToDatabase(tokenStore.exportData())
            .onSuccess { Napier.d("Exported status lists to the database", tag = "TokenStoreService") }
            .onFailure { Napier.e("Exporting status lists to the database failed", it, tag = "TokenStoreService") }
    }

    /** Imports the status lists from the database, then starts the scheduled export */
    fun initializeDatabase() {
        loadFromDatabase().getOrThrow().let { tokenStore.importData(it) }
        exportInterval.inWholeMilliseconds.let { interval ->
            exportTimer.schedule(interval, interval) { exportToDatabase() }
        }
    }

    override fun close() {
        exportTimer.cancel()
        exportToDatabase()
    }
}

class ClientTokenStoreService(
    override val tokenStore: InMemoryTokenStore,
    override val databaseService: DatabaseService,
    override val keyMaterial: KeyMaterial,
    override val statusListBaseUrl: String,
    override val exportInterval: Duration
) : TokenStoreService {
    val statusListAgent: StatusListAgent = StatusListAgent(
        keyMaterial = keyMaterial,
        statusListBaseUrl = statusListBaseUrl,
        issuerCredentialStore = tokenStore
    )

    override val exportTimer = Timer("client-status-export", true)

    override fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>> =
        databaseService.loadClientStatusLists()

    // Synchronized, as the scheduled export and the admin pages may write at the same time
    @Synchronized
    override fun saveToDatabase(data: Map<Int, Pair<Int, StatusListView>>) = databaseService.saveClientStatusLists(data)

    init {
        initializeDatabase()
    }
}

class KeyStorageTokenStoreService(
    override val tokenStore: InMemoryTokenStore,
    override val databaseService: DatabaseService,
    override val keyMaterial: KeyMaterial,
    override val statusListBaseUrl: String,
    override val exportInterval: Duration
) : TokenStoreService {
    val statusListAgent: StatusListAgent = StatusListAgent(
        keyMaterial = keyMaterial,
        statusListBaseUrl = statusListBaseUrl,
        issuerCredentialStore = tokenStore
    )

    override val exportTimer = Timer("key-storage-status-export", true)

    override fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>> =
        databaseService.loadKeyStorageStatusLists()

    // Synchronized, as the scheduled export and the admin pages may write at the same time
    @Synchronized
    override fun saveToDatabase(data: Map<Int, Pair<Int, StatusListView>>) =
        databaseService.saveKeyStorageStatusLists(data)

    init {
        initializeDatabase()
    }
}
