package at.asitplus.walletprovider

import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.encodeToAsn1Primitive
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequestAttribute
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants.CLIENT_ATTESTATION_POP_JWT
import at.asitplus.wallet.lib.jws.JwsHeaderNone
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.InstanceAttestationRequest
import at.asitplus.walletprovider.data.KeyAttestationRequest
import at.asitplus.walletprovider.injection.inject
import at.asitplus.walletprovider.injection.injectDependencies
import at.asitplus.walletprovider.service.crypto.AttestationService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ApplicationTest {
    val testConfig = ApplicationConfig("test.yaml")
    val configData = ConfigData(testConfig)

    init {
        injectDependencies(testConfig)
    }

    @Test
    fun `basic endpoint test`() = testApplication {
        environment {
            config = testConfig
        }
        application {
            moduleServer()
        }

        assertEquals(HttpStatusCode.OK, client.get(configData.endpoint.viewClientStatus).status)
        assertEquals(HttpStatusCode.OK, client.get(configData.endpoint.viewKeyStorageStatus).status)
        assertEquals(HttpStatusCode.OK, client.get(configData.endpoint.nonce).status)
        assertEquals(HttpStatusCode.OK, client.get(configData.endpoint.challenge).status)
    }

    @Test
    fun `basic status list test`() = testApplication {
        environment {
            config = testConfig
        }
        application {
            moduleServer()
        }

        client.get("${configData.endpoint.keyStorageStatus}/${configData.status.fixedTimePeriod}")
            .let {
                assertEquals(HttpStatusCode.OK, it.status)
                val token = JwsSigned.deserialize<StatusListTokenPayload>(
                    it = it.bodyAsText(),
                    deserializationStrategy = StatusListTokenPayload.serializer(),
                ).getOrThrow()
                val statusList = (token.payload.revocationList as StatusList)
                assertEquals(TokenStatusBitSize.TWO, statusList.statusBitSize)
            }
    }

    @Test
    fun `basic attestation flow`() = testApplication {
        environment {
            config = testConfig
        }
        application {
            moduleServer()
        }

        val instanceKey = EphemeralKeyWithoutCert()
        val attestationService = inject<AttestationService>()
        val instanceAttestation = attestationService.buildInstanceAttestation(
            csr = Pkcs10CertificationRequest(
                tbsCsr = TbsCertificationRequest(
                    subjectName = listOf(), publicKey = instanceKey.publicKey, listOf(), attributes = listOf(
                        Pkcs10CertificationRequestAttribute(
                            oid = ObjectIdentifier(oid = configData.provider.solutionOid), listOf(
                                joseCompliantSerializer.encodeToString(
                                    InstanceAttestationRequest(
                                        "1.0",
                                        1.days
                                    )
                                ).encodeToAsn1Primitive(),
                            )
                        )
                    )
                ),
                signatureAlgorithm = X509SignatureAlgorithm.RS256,
                rawSignature = "".encodeToAsn1Primitive()
            ),
            idx = 0
        ).getOrThrow()

        assertEquals(
            attestationService.statusListReference(0, configData.buildEndpointString(listOf(configData.endpoint.clientStatus,
                FixedTimePeriodProvider.timePeriod.toString()))),
            instanceAttestation.payload.clientStatus?.status
        )

        val nonce = attestationService.getNonce()

        val instancePop = SignJwt<JsonWebToken>(instanceKey, headerModifier = JwsHeaderNone())(
            CLIENT_ATTESTATION_POP_JWT,
            JsonWebToken(
                audience = null,
                nonce = nonce,
                issuedAt = Clock.System.now() - 5.minutes,
                expiration = Clock.System.now() + 60.minutes
            ),
            JsonWebToken.serializer(),
        ).getOrThrow()

        val keyAttestationKey = EphemeralKeyWithoutCert()


        val keyAttestationRequest = KeyAttestationRequest(
            token = instanceAttestation.jws.toString(),
            proof = instancePop.jws.toString(),
            keys = listOf(keyAttestationKey.jsonWebKey),
            keyStorage = setOf("iso_18045_high"),
            userAuthentication = setOf("iso_18045_high"),
            nonce = null,
            preferredKeyStorageStatusPeriod = 31.days,
            supportedAlgorithms = setOf()

        )

        val keyAttestation = attestationService.buildKeyAttestation(keyAttestationRequest, 0).getOrThrow()
        assertEquals(
            VerifyJwsSignature().invoke(keyAttestation.jws, attestationService.keyMaterial.publicKey).isSuccess,
            true
        )
        assertEquals(keyAttestation.payload.attestedKeys.first(), keyAttestationKey.jsonWebKey)
        assertEquals(setOf("iso_18045_high"), keyAttestation.payload.keyStorage)
        assertEquals(setOf("iso_18045_high"), keyAttestation.payload.userAuthentication)
        assertEquals(
            attestationService.statusListReference(0, configData.buildEndpointString(listOf(configData.endpoint.keyStorageStatus,
                FixedTimePeriodProvider.timePeriod.toString()))),
            keyAttestation.payload.keyStorageStatus?.status
        )
    }
}
