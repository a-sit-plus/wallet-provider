package at.asitplus.walletprovider.injection

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.crypto.KeyStoreProvider
import at.asitplus.walletprovider.service.crypto.MockAttestationService
import at.asitplus.walletprovider.service.crypto.MockKeyStoreProvider
import at.asitplus.walletprovider.service.storage.ClientTokenStoreService
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import io.ktor.server.config.*

fun injectDependencies(config: ApplicationConfig) {
    DependencyInjector.single<ConfigData> { ConfigData(config) }
    DependencyInjector.single<DatabaseService> { DatabaseService(inject()) }
    DependencyInjector.single<KeyStoreProvider>(::MockKeyStoreProvider)
    DependencyInjector.factory<InMemoryTokenStore> { InMemoryTokenStore() }
    DependencyInjector.single<ClientTokenStoreService> {
        val configData = inject<ConfigData>()
        ClientTokenStoreService(
            tokenStore = inject(),
            databaseService = inject(),
            keyMaterial = inject(),
            statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.clientStatus)),
            exportInterval = configData.database.exportInterval
        )
    }
    DependencyInjector.single<KeyStorageTokenStoreService> {
        val configData = inject<ConfigData>()
        KeyStorageTokenStoreService(
            tokenStore = inject(),
            databaseService = inject(),
            keyMaterial = inject(),
            statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.keyStorageStatus)),
            exportInterval = configData.database.exportInterval
        )
    }
    DependencyInjector.single<KeyMaterial> { inject<KeyStoreProvider>().getSigner() }
    DependencyInjector.single<AttestationService> { MockAttestationService(inject(), inject()) }
}