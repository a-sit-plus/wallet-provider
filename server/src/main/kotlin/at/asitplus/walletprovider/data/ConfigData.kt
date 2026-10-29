package at.asitplus.walletprovider.data

import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

/**
 * All settings of the wallet provider, bound from `application.yaml` (or the Spring Cloud Config server).
 * The `supreme` section is loaded by Warden Supreme, see [at.asitplus.walletprovider.config.ProviderBeans.supremeConfiguration].
 */
@Component
class ConfigData(
    val database: DatabaseConfigData,
    val keystore: KeyStoreConfigData,
    val provider: ProviderConfigData,
    val endpoint: EndpointConfigData,
    val attestation: AttestationConfigData,
    val admin: AdminConfigData,
) {
    val status = StatusListConfigData()

    /** [ProviderConfigData.publicContext] with [paths] appended, e.g. `https://provider.example/api/v1/clientStatus/1` */
    val buildEndpointString: (List<String>) -> String = { paths ->
        UriComponentsBuilder.fromUriString(provider.publicContext)
            .pathSegment(*paths.flatMap { it.split('/') }.filter { it.isNotEmpty() }.toTypedArray())
            .build()
            .toUriString()
    }
}

@ConfigurationProperties("provider")
data class ProviderConfigData(
    val publicContext: String,
    val clientId: String,
    val issuer: String,
    val solutionId: String,
    val solutionOid: String,
) {
    val solutionCertificationInfo = issuer
    val storageCertificationInfo = issuer
}

@ConfigurationProperties("endpoints")
data class EndpointConfigData(
    val challenge: String,
    val instanceAttestation: String,
    val keyAttestation: String,
    val clientStatus: String,
    val keyStorageStatus: String,
    val nonce: String,
)

@ConfigurationProperties("attestation")
data class AttestationConfigData(
    val keyAttestation: AttestationDurations,
    val instanceAttestation: AttestationDurations,
) {
    data class AttestationDurations(
        val maintenance: Duration,
        val lifetime: Duration,
    )
}

/** How often the status lists are written to the database; the database itself is `spring.datasource`. */
@ConfigurationProperties("database")
data class DatabaseConfigData(
    val exportInterval: Duration,
)

@ConfigurationProperties("keystore")
data class KeyStoreConfigData(
    val file: String,
    val alias: String,
    val secret: String,
)

/** The single operator account of the admin pages; with an empty [password] nobody can log in. */
@ConfigurationProperties("admin")
data class AdminConfigData(
    val username: String = "admin",
    val password: String = "",
)

class StatusListConfigData {
    /** The only time period: the attestations reference the status lists of [FixedTimePeriodProvider] */
    val fixedTimePeriod = FixedTimePeriodProvider.timePeriod
}
