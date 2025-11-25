package at.asitplus.walletprovider.service.crypto

import at.asitplus.attestation.supreme.AttestationResponse
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.DefaultNonceService
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.walletprovider.data.ConfigData

class MockAttestationService(
    override val configData: ConfigData,
    override val keyMaterial: KeyMaterial,
) : AttestationService {
    override val nonceService = DefaultNonceService()

    override suspend fun verifyKeyAttestedKeys(csr: Pkcs10CertificationRequest) = AttestationResponse.Success(listOf())
    override suspend fun issueChallenge() = nonceService.provideNonce()
}
