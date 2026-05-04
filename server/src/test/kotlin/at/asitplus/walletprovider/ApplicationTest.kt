package at.asitplus.walletprovider

import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.encodeToAsn1Primitive
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequestAttribute
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants.CLIENT_ATTESTATION_POP_JWT
import at.asitplus.wallet.lib.jws.JwsHeaderNone
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.UnitAttestationRequest
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

        assertEquals(HttpStatusCode.OK, client.get(configData.endpoint.root).status)
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

        client.get("${configData.endpoint.status}/${configData.status.fixedTimePeriod}")
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
            Pkcs10CertificationRequest(
                tbsCsr = TbsCertificationRequest(
                    subjectName = listOf(), publicKey = instanceKey.publicKey, listOf(), attributes = listOf(
                        Pkcs10CertificationRequestAttribute(
                            oid = ObjectIdentifier(oid = configData.provider.solutionOid), listOf(
                                "1.0".encodeToAsn1Primitive()
                            )
                        )
                    )
                ),
                signatureAlgorithm = X509SignatureAlgorithm.RS256,
                rawSignature = "".encodeToAsn1Primitive()
            )
        ).getOrThrow()

        val nonce = attestationService.getNonce()

        val instancePop = SignJwt<JsonWebToken>(instanceKey, headerModifier = JwsHeaderNone())(
            CLIENT_ATTESTATION_POP_JWT,
            JsonWebToken(
                audience = null,
                nonce = nonce,
                issuedAt = Clock.System.now() - 5.minutes,
                expiration = Clock.System.now() + 60.minutes
            ),
            JsonWebToken.Companion.serializer(),
        ).getOrThrow()

        val unitKey = EphemeralKeyWithoutCert()


        val unitAttestationRequest = UnitAttestationRequest(
            token = instanceAttestation.serialize(),
            proof = instancePop.serialize(),
            keys = listOf(unitKey.jsonWebKey),
            keyStorage = setOf("iso_18045_high"),
            userAuthentication = setOf("iso_18045_high"),

        )

        val unitAttestation = attestationService.buildUnitAttestation(unitAttestationRequest, 0).getOrThrow()
        assertEquals(
            VerifyJwsSignature().invoke(unitAttestation, attestationService.keyMaterial.publicKey).isSuccess,
            true
        )
        assertEquals(unitAttestation.payload.attestedKeys.first(), unitKey.jsonWebKey)
        assertEquals(setOf("iso_18045_high"), unitAttestation.payload.keyStorage)
        assertEquals(setOf("iso_18045_high"), unitAttestation.payload.userAuthentication)
        assertEquals(attestationService.statusListReference(0), unitAttestation.payload.keyStorageStatus?.status)
    }
}
