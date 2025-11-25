package at.asitplus.walletprovider.injection

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.crypto.KeyStoreProvider
import at.asitplus.walletprovider.service.crypto.MockAttestationService
import at.asitplus.walletprovider.service.crypto.MockKeyStoreProvider
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import io.ktor.server.config.*

fun injectDependencies(config: ApplicationConfig) {
    DependencyInjector.single { ConfigData(config) }
    DependencyInjector.single<KeyStoreProvider>(::MockKeyStoreProvider)
    DependencyInjector.single{InMemoryTokenStore(configData = inject(), databaseService = inject())}
    DependencyInjector.single<KeyMaterial> { inject<KeyStoreProvider>().getSigner() }
    DependencyInjector.single {
        val configData: ConfigData = inject()
        val statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.status))
        StatusListAgent(
            statusListBaseUrl = statusListBaseUrl,
            keyMaterial = inject(),
            issuerCredentialStore = inject<InMemoryTokenStore>()
        )
    }
    DependencyInjector.single<AttestationService> { MockAttestationService(inject(), inject()) }
}