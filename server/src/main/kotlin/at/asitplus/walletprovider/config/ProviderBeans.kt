package at.asitplus.walletprovider.config

import at.asitplus.attestation.fromSpringEnvironment
import at.asitplus.attestation.supreme.SupremeConfiguration
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.crypto.KeyStoreProvider
import at.asitplus.walletprovider.service.crypto.RealAttestationService
import at.asitplus.walletprovider.service.crypto.RealKeyStoreProvider
import at.asitplus.walletprovider.service.network.HttpService
import at.asitplus.walletprovider.service.storage.ClientTokenStoreService
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import kotlin.time.toKotlinDuration

/**
 * Wiring of the provider's services. The key store and the attestation verification are replaced by mocks in the
 * `test` profile.
 */
@Configuration
class ProviderBeans {

    /** Created after Flyway has migrated the database */
    @Bean
    @DependsOnDatabaseInitialization
    fun databaseService(
        jdbc: JdbcTemplate,
        transactions: TransactionTemplate,
        @Value("\${spring.flyway.default-schema}") schema: String,
    ) = DatabaseService(jdbc, transactions, schema)

    @Bean
    @Profile("!test")
    fun keyStoreProvider(configData: ConfigData): KeyStoreProvider = RealKeyStoreProvider(configData)

    @Bean
    fun keyMaterial(keyStoreProvider: KeyStoreProvider): KeyMaterial = keyStoreProvider.getSigner()

    @Bean
    fun clientTokenStoreService(configData: ConfigData, databaseService: DatabaseService, keyMaterial: KeyMaterial) =
        ClientTokenStoreService(
            tokenStore = InMemoryTokenStore(),
            databaseService = databaseService,
            keyMaterial = keyMaterial,
            statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.clientStatus)),
            exportInterval = configData.database.exportInterval.toKotlinDuration()
        )

    @Bean
    fun keyStorageTokenStoreService(configData: ConfigData, databaseService: DatabaseService, keyMaterial: KeyMaterial) =
        KeyStorageTokenStoreService(
            tokenStore = InMemoryTokenStore(),
            databaseService = databaseService,
            keyMaterial = keyMaterial,
            statusListBaseUrl = configData.buildEndpointString(listOf(configData.endpoint.keyStorageStatus)),
            exportInterval = configData.database.exportInterval.toKotlinDuration()
        )

    /**
     * The section `supreme`, loaded through Warden Supreme's Spring binding: Spring resolves the property sources (e.g.
     * the config server before `application.yaml`), Warden Supreme validates and applies its defaults.
     */
    @Bean
    fun supremeConfiguration(environment: Environment): SupremeConfiguration =
        SupremeConfiguration.fromSpringEnvironment(environment, "supreme")

    @Bean
    @Profile("!test")
    fun attestationService(
        configData: ConfigData,
        keyMaterial: KeyMaterial,
        supremeConfiguration: SupremeConfiguration,
    ): AttestationService = RealAttestationService(configData, keyMaterial, supremeConfiguration)

    @Bean
    fun httpService(
        configData: ConfigData,
        clientTokenStoreService: ClientTokenStoreService,
        keyStorageTokenStoreService: KeyStorageTokenStoreService,
        attestationService: AttestationService,
    ) = HttpService(configData, clientTokenStoreService, keyStorageTokenStoreService, attestationService)
}
