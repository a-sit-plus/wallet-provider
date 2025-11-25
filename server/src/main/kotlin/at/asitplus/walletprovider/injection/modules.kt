package at.asitplus.walletprovider.injection

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.crypto.KeyStoreProvider
import at.asitplus.walletprovider.service.crypto.RealAttestationService
import at.asitplus.walletprovider.service.crypto.RealKeyStoreProvider
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import io.ktor.server.config.*

fun injectDependencies(config: ApplicationConfig) {
    DependencyInjector.single<ConfigData> { ConfigData(config) }
    DependencyInjector.single<KeyStoreProvider> { RealKeyStoreProvider(inject<ConfigData>()) }
    DependencyInjector.single<InMemoryTokenStore> { InMemoryTokenStore(databaseService = inject<DatabaseService>(), configData = inject<ConfigData>()) }
    DependencyInjector.single<KeyMaterial> { inject<KeyStoreProvider>().getSigner() }
    DependencyInjector.single {
        val configData: ConfigData = inject()
        val statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.status))
        val inMemoryTokenStore = inject<InMemoryTokenStore>()
        val database = inject<DatabaseService>()
        database.loadStatusLists().getOrNull()?.let {
            inMemoryTokenStore.importFromDatabase(it)
        }

        StatusListAgent(
            statusListBaseUrl = statusListBaseUrl,
            keyMaterial = inject(),
            issuerCredentialStore = inMemoryTokenStore
        )
    }
    DependencyInjector.single<AttestationService> { RealAttestationService(inject(), inject()) }
}
