package at.asitplus.walletprovider.data

import at.asitplus.openid.DurationSecondsIntSerializer
import at.asitplus.signum.indispensable.josef.JsonWebKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

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
