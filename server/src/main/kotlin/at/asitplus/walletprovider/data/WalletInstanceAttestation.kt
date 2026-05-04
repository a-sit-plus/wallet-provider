package at.asitplus.walletprovider.data

import at.asitplus.signum.indispensable.josef.ClientStatus
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.oidvci.BuildClientAttestationJwt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object BuildInstanceAttestationJwt {
    suspend operator fun invoke(
        signJwt: SignJwtFun<JsonWebToken>,
        clientId: String,
        clientKey: JsonWebKey,
        walletName: String,
        walletVersion: String,
        walletSolutionCertificationInformation: String,
        clientStatus: ClientStatus,
        walletLink: String? = null,
        lifetime: Duration = 60.minutes,
        clockSkew: Duration = 5.minutes,
    ) = BuildClientAttestationJwt(
        signJwt = signJwt,
        clientId = clientId,
        clientKey = clientKey,
        walletName = walletName,
        walletVersion = walletVersion,
        walletSolutionCertificationInformation = walletSolutionCertificationInformation,
        clientStatus = clientStatus,
        walletLink = walletLink,
        lifetime = lifetime,
        clockSkew = clockSkew,
    )
}
