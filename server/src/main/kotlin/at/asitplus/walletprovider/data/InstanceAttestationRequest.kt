package at.asitplus.walletprovider.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class InstanceAttestationRequest(
    val versionName: String,
    val preferredClientStatusPeriod: Duration? = null
)