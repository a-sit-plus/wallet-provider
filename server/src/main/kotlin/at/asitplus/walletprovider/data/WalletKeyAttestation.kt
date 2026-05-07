package at.asitplus.walletprovider.data

import at.asitplus.openid.DurationSecondsIntSerializer
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.KeyStorageStatus
import at.asitplus.wallet.lib.jws.SignJwtFun
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object BuildKeyAttestationJwt {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        signJwt: SignJwtFun<KeyAttestationJwt>,
        attestedKeys: List<JsonWebKey>,
        keyStorage: Collection<String>,
        userAuthentication: Collection<String>,
        certification: String,
        keyStorageStatus: KeyStorageStatus,
        nonce: String? = null,
        lifetime: Duration = 40.days,
        clockSkew: Duration = 5.minutes,
    ) = signJwt(
        OpenIdConstants.KEY_ATTESTATION_JWT_TYPE,
        KeyAttestationJwt(
            issuedAt = now() - clockSkew,
            expiration = now() - clockSkew + lifetime,
            attestedKeys = attestedKeys,
            keyStorage = keyStorage,
            userAuthentication = userAuthentication,
            certification = certification,
            keyStorageStatus = keyStorageStatus,
            nonce = nonce,
        ),
        KeyAttestationJwt.serializer(),
    ).getOrThrow()
}

@Serializable
data class KeyAttestationRequest(
    @SerialName("token") val token: String,
    @SerialName("proof") val proof: String,
    @SerialName("keys") val keys: List<JsonWebKey>,
    @SerialName("nonce") val nonce: String?,
    @SerialName("key_storage") val keyStorage: Set<String>,
    @SerialName("user_authentication") val userAuthentication: Set<String>,
    @SerialName("preferred_key_storage_status_period")
    @Serializable(with = DurationSecondsIntSerializer::class)
    val preferredKeyStorageStatusPeriod: Duration?,
    @SerialName("supported_algorithms") val supportedAlgorithms: Collection<String>?,
)
