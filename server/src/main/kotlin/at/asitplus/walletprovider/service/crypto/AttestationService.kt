package at.asitplus.walletprovider.service.crypto

import at.asitplus.attestation.supreme.AttestationResponse
import at.asitplus.attestation.supreme.AttestationVerifier
import at.asitplus.catching
import at.asitplus.catchingUnwrapped
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.josef.*
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.DefaultNonceService
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.wallet.lib.oidvci.BuildClientAttestationJwt
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.InstanceAttestationRequest
import at.asitplus.walletprovider.data.KeyAttestationRequest
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Clock.System.now
import kotlin.time.Duration
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

    suspend fun buildInstanceAttestation(csr: Pkcs10CertificationRequest, idx: Int) = catchingUnwrapped {
        val csrData = (csr.tbsCsr.attributes.firstOrNull {
            it.oid.toString() == configData.provider.solutionOid
        }?.value)

        val request = (csrData?.firstOrNull() as? Asn1Primitive)?.content?.toString(Charsets.UTF_8)?.let {
            joseCompliantSerializer.decodeFromString<InstanceAttestationRequest>(it)
        } ?: throw Throwable("No InstanceAttestationRequest in CSR!")

        val walletSolutionVersion = request.versionName
        val preferredClientStatusPeriod = request.preferredClientStatusPeriod

        verifyKeyAttestedKeys(csr).let { response ->
            when (response) {
                is AttestationResponse.Success -> {
                    val clientKey = csr.tbsCsr.publicKey.toJsonWebKey()
                    Napier.i("Verified key $clientKey", tag = "AttestationService")
                    return@catchingUnwrapped BuildClientAttestationJwt(
                        SignJwt(keyMaterial, JwsHeaderCertOrJwk()),
                        clientId = configData.provider.clientId,
                        issuer = configData.provider.issuer,
                        lifetime = configData.attestation.instanceAttestation.lifetime,
                        clientKey = clientKey,
                        walletName = configData.provider.solutionId,
                        walletVersion = walletSolutionVersion,
                        walletSolutionCertificationInformation = configData.provider.solutionCertificationInfo,
                        clientStatus = ClientStatus(
                            status = statusListReference(
                                idx = idx, configData.buildEndpointString(
                                    listOf(
                                        configData.endpoint.clientStatus,
                                        FixedTimePeriodProvider.timePeriod.toString()
                                    )
                                )
                            ),
                            expiration = now() + configData.attestation.instanceAttestation.maintenance,
                        )
                    )
                }

                is AttestationResponse.Failure -> {
                    throw Throwable("AttestationResponse.Failure: Type: ${response.kind}, Explanation: ${response.explanation}")
                }
            }
        }
    }

    suspend fun verifyInstanceAttestation(token: JwsCompactTyped<JsonWebToken>, proof: JwsCompactTyped<JsonWebToken>) =
        catchingUnwrapped {
            val clientKey = token.payload.confirmationClaim?.jsonWebKey?.toCryptoPublicKey()?.getOrThrow()!!
            val instanceAttestationValid =
                VerifyJwsSignature().invoke(token.jws, keyMaterial.publicKey).isSuccess
            val proofValid = VerifyJwsSignature().invoke(proof.jws, clientKey).isSuccess
            val nonceValid = verifyNonce(proof.payload.nonce!!)
            return@catchingUnwrapped (instanceAttestationValid && proofValid && nonceValid)
        }

    suspend fun buildKeyAttestation(request: KeyAttestationRequest, idx: Int) = catchingUnwrapped {
        val token = catching { JwsCompactTyped<JsonWebToken>(request.token) }.getOrThrow()

        val proof = catching { JwsCompactTyped<JsonWebToken>(request.proof) }.getOrThrow()


        when (verifyInstanceAttestation(token, proof).getOrDefault(false)) {
            true -> {
                return@catchingUnwrapped BuildKeyAttestationJwt(
                    SignJwt(keyMaterial, JwsHeaderCertOrJwk()),
                    lifetime = configData.attestation.keyAttestation.lifetime,
                    attestedKeys = request.keys,
                    keyStorage = request.keyStorage,
                    userAuthentication = request.userAuthentication,
                    certification = configData.provider.storageCertificationInfo,
                    keyStorageStatus = KeyStorageStatus(
                        status = statusListReference(
                            idx, configData.buildEndpointString(
                                listOf(
                                    configData.endpoint.keyStorageStatus,
                                    FixedTimePeriodProvider.timePeriod.toString()
                                )
                            )
                        ),
                        expiration = now() + configData.attestation.keyAttestation.maintenance,
                    ),
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

    fun statusListReference(idx: Int, endpoint: String): JsonObject = buildJsonObject {
        put(
            key = "status_list", element = joseCompliantSerializer.encodeToJsonElement(
                StatusListInfo(index = idx.toULong(), endpoint.toUri())
            )
        )
    }

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
}

class RealAttestationService(
    override val configData: ConfigData,
    override val keyMaterial: KeyMaterial
) : AttestationService {
    override val nonceService = DefaultNonceService()

    val attestationValidator = AttestationVerifier(
        configuration = configData.attestation.supremeConfiguration
    )

    @OptIn(ExperimentalTime::class)
    override suspend fun verifyKeyAttestedKeys(csr: Pkcs10CertificationRequest): AttestationResponse =
        attestationValidator.verifyAttestation(
            csr = csr,
            onPreAttestationError = {
                "onPreAttestationError: type: ${this.javaClass.simpleName}, errorId: ${createErrorId()}}".let {
                    Napier.w("$it, message: ${this.throwable}, $csr")
                    it
                }
            },
            onAttestationError = { debugStatement ->
                "onAttestationError: type: ${this.javaClass.simpleName}, errorId: ${createErrorId()}".let {
                    Napier.w("$it, message: ${debugStatement.serializeCompact()}, $csr", cause)
                    it
                }
            }
        ) { listOf() }

    override suspend fun issueChallenge() = Json.encodeToString(
        attestationValidator.issueChallenge(
            timeZone = TimeZone.currentSystemDefault(),
            postEndpoint = configData.endpoint.instanceAttestation,
        )
    )
}

fun createErrorId() = Random.nextUInt().toString(radix = 36)
