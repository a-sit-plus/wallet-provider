package at.asitplus.walletprovider.data

import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.EudiWalletInfo
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object BuildUnitAttestationJwt {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        signJwt: SignJwtFun<KeyAttestationJwt>,
        clientId: String,
        issuer: String,
        walletName: String? = null,
        walletLink: String? = null,
        walletInfo: EudiWalletInfo? = null,
        status: JsonObject? = null,
        attestedKeys: List<JsonWebKey>,
        nonce: String? = null,
        lifetime: Duration = 40.days,
        clockSkew: Duration = 5.minutes,
    ) = signJwt(
        OpenIdConstants.KEY_ATTESTATION_JWT_TYPE,
        KeyAttestationJwt(
            issuer = issuer,
            subject = clientId,
            issuedAt = now() - clockSkew,
            expiration = now() - clockSkew + lifetime,
            eudiWalletInfo = walletInfo,
            attestedKeys = attestedKeys,
            status = status,
            nonce = nonce
        ),
        KeyAttestationJwt.Companion.serializer(),
    ).getOrThrow()
}

@Serializable
data class UnitAttestationRequest(
    @SerialName("token") val token: String,
    @SerialName("proof") val proof: String,
    @SerialName("keys") val keys: List<JsonWebKey>,
    @SerialName("storage_type") val storageType: String,
)