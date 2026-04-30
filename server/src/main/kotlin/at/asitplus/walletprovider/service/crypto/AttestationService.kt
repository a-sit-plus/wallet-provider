package at.asitplus.walletprovider.service.crypto

import at.asitplus.attestation.IosAttestationConfiguration
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.attestation.supreme.AttestationResponse
import at.asitplus.attestation.supreme.AttestationVerifier
import at.asitplus.attestation.supreme.SupremeConfiguration
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.josef.*
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.DefaultNonceService
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.walletprovider.data.BuildInstanceAttestationJwt
import at.asitplus.walletprovider.data.BuildUnitAttestationJwt
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.UnitAttestationRequest
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

interface AttestationService {
    val configData: ConfigData
    val keyMaterial: KeyMaterial
    val nonceService: DefaultNonceService

    suspend fun verifyKeyAttestedKeys(
        csr: Pkcs10CertificationRequest
    ): AttestationResponse

    suspend fun buildInstanceAttestation(csr: Pkcs10CertificationRequest) = runCatching {
        val walletSolutionVersion = (csr.tbsCsr.attributes.firstOrNull {
            it.oid.toString() == configData.provider.solutionOid
        }?.value?.first() as? Asn1Primitive)?.content?.toString(Charsets.UTF_8)
            ?: throw Throwable("walletSolutionVersion missing")

        when (verifyKeyAttestedKeys(csr)) {
            is AttestationResponse.Success -> {
                val clientKey = csr.tbsCsr.publicKey.toJsonWebKey()
                Napier.i("Verified key $clientKey", tag = "AttestationService")
                return@runCatching BuildInstanceAttestationJwt(
                    SignJwt(keyMaterial, JwsHeaderCertOrJwk()),
                    clientId = configData.provider.clientId,
                    issuer = configData.provider.issuer,
                    lifetime = 60.minutes,
                    clientKey = clientKey,
                    walletInfo = EudiWalletInfo(
                        GeneralInfo(
                            walletProviderName = configData.provider.providerName,
                            walletSolutionId = configData.provider.solutionId,
                            walletSolutionVersion = walletSolutionVersion,
                            walletSolutionCertificationInformation = configData.provider.solutionCertificationInfo
                        )
                    )
                )
            }

            is AttestationResponse.Failure -> {
                throw Throwable("KeyAttestedKeys invalid")
            }
        }
    }

    suspend fun verifyInstanceAttestation(token: JwsSigned<JsonWebToken>, proof: JwsSigned<JsonWebToken>) =
        runCatching {
            val clientKey = token.payload.confirmationClaim?.jsonWebKey?.toCryptoPublicKey()?.getOrThrow()!!
            val instanceAttestationValid =
                VerifyJwsSignature().invoke(token, keyMaterial.publicKey).isSuccess
            val proofValid = VerifyJwsSignature().invoke(proof, clientKey).isSuccess
            val nonceValid = verifyNonce(proof.payload.nonce!!)
            return@runCatching (instanceAttestationValid && proofValid && nonceValid)
        }

    suspend fun buildUnitAttestation(request: UnitAttestationRequest, idx: Int) = runCatching {
        val token = JwsSigned.deserialize<JsonWebToken>(
            it = request.token,
            deserializationStrategy = JsonWebToken.Companion.serializer(),
        ).getOrThrow()

        val proof = JwsSigned.deserialize<JsonWebToken>(
            it = request.proof,
            deserializationStrategy = JsonWebToken.Companion.serializer(),
        ).getOrThrow()

        val walletSolutionVersion = token.payload.eudiWalletInfo?.generalInfo?.walletSolutionVersion ?: throw Throwable(
            "walletSolutionVersion not found"
        )

        when (verifyInstanceAttestation(token, proof).getOrDefault(false)) {
            true -> {
                return@runCatching BuildUnitAttestationJwt(
                    SignJwt(keyMaterial, JwsHeaderCertOrJwk()),
                    clientId = configData.provider.clientId,
                    issuer = configData.provider.issuer,
                    lifetime = 40.days,
                    walletInfo = EudiWalletInfo(
                        GeneralInfo(
                            walletProviderName = configData.provider.providerName,
                            walletSolutionId = configData.provider.solutionId,
                            walletSolutionVersion = walletSolutionVersion,
                            walletSolutionCertificationInformation = configData.provider.solutionCertificationInfo
                        ), KeyStorageInfo(
                            storageType = request.storageType,
                            storageCertificationInformation = configData.provider.storageCertificationInfo,
                        )
                    ),
                    status = buildJsonObject { // because is JsonObject in data class
                        putJsonObject("status_list")
                        {
                            put("idx", idx)
                            put(
                                "uri",
                                configData.buildEndpointString(
                                    listOf(
                                        configData.endpoint.status,
                                        configData.status.fixedTimePeriod.toString()
                                    )
                                )
                            )
                        }
                    },
                    attestedKeys = request.keys,
                )
            }

            false -> {

                throw Throwable("InstanceAttestation invalid")
            }
        }
    }

    suspend fun issueChallenge(): String
    suspend fun getNonce() = nonceService.provideNonce()
    suspend fun verifyNonce(nonce: String) = nonceService.verifyAndRemoveNonce(nonce)
}

class RealAttestationService(
    override val configData: ConfigData,
    override val keyMaterial: KeyMaterial
) : AttestationService {
    override val nonceService = DefaultNonceService()

    val attestationValidator = AttestationVerifier(
        SupremeConfiguration(
            AndroidAttestationConfiguration.Builder(
                AndroidAttestationConfiguration.AppData(
                    configData.attestation.androidPackageName,
                    setOf(
                        configData.attestation.androidSignerFingerprint.hexToByteArray(
                            HexFormat.Default
                        )
                    )
                )
            )
                .build(),
            IosAttestationConfiguration(
                IosAttestationConfiguration.AppData(
                    configData.attestation.iosTeamIdentifier,
                    configData.attestation.iosBundleIdentifier,
                    sandbox = true
                ),
            ),
            clock = object : SupremeConfiguration.Clock {
                override val timeSource: Clock
                    get() = Clock.System
            })
    )


    @OptIn(ExperimentalTime::class)
    override suspend fun verifyKeyAttestedKeys(csr: Pkcs10CertificationRequest): AttestationResponse =
        attestationValidator.verifyAttestation(
            csr
        ) { listOf() }

    override suspend fun issueChallenge() = Json.encodeToString(
        attestationValidator.issueChallenge(
            timeZone = TimeZone.currentSystemDefault(),
            postEndpoint = configData.endpoint.instance,
        )
    )
}