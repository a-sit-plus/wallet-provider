package at.asitplus.walletprovider.data

import at.asitplus.signum.indispensable.josef.ConfirmationClaim
import at.asitplus.signum.indispensable.josef.EudiWalletInfo
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.jws.SignJwtFun
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object BuildInstanceAttestationJwt {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        signJwt: SignJwtFun<JsonWebToken>,
        clientId: String,
        issuer: String,
        clientKey: JsonWebKey,
        walletName: String? = null,
        walletLink: String? = null,
        walletInfo: EudiWalletInfo? = null,
        lifetime: Duration = 60.minutes,
        clockSkew: Duration = 5.minutes,
    ) = signJwt(
        JwsContentTypeConstants.CLIENT_ATTESTATION_JWT,
        JsonWebToken(
            issuer = issuer,
            subject = clientId,
            issuedAt = Clock.System.now() - clockSkew,
            expiration = Clock.System.now() - clockSkew + lifetime,
            walletName = walletName,
            walletLink = walletLink,
            eudiWalletInfo = walletInfo,
            confirmationClaim = ConfirmationClaim(
                jsonWebKey = clientKey,
            )
        ),
        JsonWebToken.Companion.serializer(),
    ).getOrThrow()
}