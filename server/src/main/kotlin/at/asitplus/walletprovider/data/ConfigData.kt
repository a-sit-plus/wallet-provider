package at.asitplus.walletprovider.data

import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.util.Attributes
import kotlin.time.Duration

data class ConfigData(
    val config: ApplicationConfig
) {
    val ktor = KtorConfigData(config)
    val database = DatabaseConfigData(config)
    val keystore by lazy { KeyStoreConfigData(config) }
    val provider = ProviderConfigData(config)
    val endpoint = EndpointConfigData(config)
    val attestation = AttestationConfigData(config)
    val status = StatusListConfigData(config)

    val buildEndpointString: (List<String>) -> String = { paths ->
        URLBuilder(Url.invoke(provider.publicContext))
            .appendPathSegments(paths)
            .buildString()
    }
}

data class KtorConfigData(
    val config: ApplicationConfig
) {
    val deployment = Deployment(
        port = config.propertyOrNull("ktor.deployment.port")?.getString()
            ?: throw Throwable("ktor.deployment.port is missing from config"),
        host = config.propertyOrNull("ktor.deployment.host")?.getString()
            ?: throw Throwable("ktor.deployment.host is missing from config")
    )

    data class Deployment(
        val port: String,
        val host: String
    )
}

data class ProviderConfigData(
    val config: ApplicationConfig
) {
    val publicContext = config.propertyOrNull("provider.publicContext")?.getString()
        ?: throw Throwable("provider.publicContext is missing from config")
    val clientId = config.propertyOrNull("provider.clientId")?.getString()
        ?: throw Throwable("provider.clientId is missing from config")
    val issuer = config.propertyOrNull("provider.issuer")?.getString()
        ?: throw Throwable("provider.issuer is missing from config")
    val providerName = config.propertyOrNull("provider.providerName")?.getString()
        ?: throw Throwable("provider.providerName is missing from config")
    val solutionId = config.propertyOrNull("provider.solutionId")?.getString()
        ?: throw Throwable("provider.solutionId is missing from config")
    val solutionOid = config.propertyOrNull("provider.solutionOid")?.getString()
        ?: throw Throwable("provider.solutionOid is missing from config")
    val solutionCertificationInfo = issuer
    val storageCertificationInfo = issuer
}

data class EndpointConfigData(
    val config: ApplicationConfig
) {
    val challenge = config.propertyOrNull("endpoints.challenge")?.getString()
        ?: throw Throwable("endpoints.challenge is missing from config")
    val instanceAttestation = config.propertyOrNull("endpoints.instanceAttestation")?.getString()
        ?: throw Throwable("endpoints.instanceAttestation is missing from config")
    val keyAttestation =
        config.propertyOrNull("endpoints.keyAttestation")?.getString()
            ?: throw Throwable("endpoints.keyAttestation is missing from config")
    val clientStatus = config.propertyOrNull("endpoints.clientStatus")?.getString()
        ?: throw Throwable("endpoints.clientStatus is missing from config")

    val keyStorageStatus = config.propertyOrNull("endpoints.keyStorageStatus")?.getString()
        ?: throw Throwable("endpoints.keyStorageStatus is missing from config")
    val nonce = config.propertyOrNull("endpoints.nonce")?.getString()
        ?: throw Throwable("endpoints.nonce is missing from config")
    val update = config.propertyOrNull("endpoints.update")?.getString()
        ?: throw Throwable("endpoints.update is missing from config")
    val root =
        config.propertyOrNull("endpoints.root")?.getString() ?: throw Throwable("endpoints.root is missing from config")
}

data class AttestationConfigData(
    val config: ApplicationConfig
) {
    val keyAttestation = AttestationDurations(
        maintenance = config.propertyOrNull("attestation.keyAttestation.maintenance")?.getString()?.let { Duration.parseIsoString(it) }
            ?: throw Throwable("attestation.keyAttestation.maintenance is missing from config"),
        lifetime = config.propertyOrNull("attestation.keyAttestation.lifetime")?.getString()?.let { Duration.parseIsoString(it) }
            ?: throw Throwable("attestation.keyAttestation.lifetime is missing from config")
    )
    val instanceAttestation = AttestationDurations(
        maintenance = config.propertyOrNull("attestation.instanceAttestation.maintenance")?.getString()?.let { Duration.parseIsoString(it) }
            ?: throw Throwable("attestation.instanceAttestation.maintenance is missing from config"),
        lifetime = config.propertyOrNull("attestation.instanceAttestation.lifetime")?.getString()?.let { Duration.parseIsoString(it) }
            ?: throw Throwable("attestation.instanceAttestation.lifetime is missing from config")
    )
    val androidPackageName = config.propertyOrNull("attestation.android.packageName")?.getString()
        ?: throw Throwable("attestation.android.packageName is missing from config")
    val androidSignerFingerprint =
        config.propertyOrNull("attestation.android.signerFingerprint")?.getString()
            ?: throw Throwable("attestation.android.signerFingerprint is missing from config")
    val iosTeamIdentifier = config.propertyOrNull("attestation.ios.teamIdentifier")?.getString()
        ?: throw Throwable("attestation.ios.teamIdentifier is missing from config")
    val iosBundleIdentifier = config.propertyOrNull("attestation.ios.bundleIdentifier")?.getString()
        ?: throw Throwable("attestation.ios.bundleIdentifier is missing from config")

    data class AttestationDurations(
        val maintenance: Duration,
        val lifetime: Duration
    )
}

data class DatabaseConfigData(
    val config: ApplicationConfig
) {
    val url = config.propertyOrNull("database.url")?.getString()
        ?: throw Throwable("database.url is missing from config")
    val driver = config.propertyOrNull("database.driver")?.getString()
        ?: throw Throwable("database.driver is missing from config")

    val exportInterval = config.propertyOrNull("database.exportInterval")?.getString()?.let { Duration.parseIsoString(it) }
        ?: throw Throwable("database.exportInterval is missing from config")
}

data class KeyStoreConfigData(
    val config: ApplicationConfig
) {
    val file: String = config.propertyOrNull("keystore.file")?.getString()
        ?: throw Throwable("keystore.file is missing from config")

    val alias: String = config.propertyOrNull("keystore.alias")?.getString()
        ?: throw Throwable("keystore.file is missing from config")

    val secret: String = config.propertyOrNull("keystore.secret")?.getString()
        ?: throw Throwable("keystore.file is missing from config")
}

data class StatusListConfigData(
    val config: ApplicationConfig
) {
    val fixedTimePeriod = 1
}