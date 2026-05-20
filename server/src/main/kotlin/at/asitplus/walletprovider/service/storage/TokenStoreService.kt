package at.asitplus.walletprovider.service.storage

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import io.github.aakira.napier.Napier
import java.util.*
import kotlin.concurrent.schedule
import kotlin.time.Duration

interface TokenStoreService {
    val tokenStore: InMemoryTokenStore
    val databaseService: DatabaseService
    val keyMaterial: KeyMaterial
    val statusListBaseUrl: String
    val exportInterval: Duration

    fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>>
    fun exportToDatabase()

    fun initializeDatabase() {
        Timer().let {
            it.schedule(0L, exportInterval.inWholeMilliseconds) {
                exportToDatabase()
                Napier.i("Scheduled database write.", tag = "TokenStoreService")
            }
        }

        loadFromDatabase().onSuccess {
            tokenStore.importData(it)
        }
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

    override fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>> =
        databaseService.loadClientStatusLists()

    override fun exportToDatabase() {
        databaseService.saveClientStatusLists(tokenStore.exportData())
    }

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

    override fun loadFromDatabase(): Result<Map<Int, Pair<Int, StatusListView>>> =
        databaseService.loadKeyStorageStatusLists()

    override fun exportToDatabase() {
        databaseService.saveKeyStorageStatusLists(tokenStore.exportData())
    }

    init {
        initializeDatabase()
    }
}