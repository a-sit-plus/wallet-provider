package at.asitplus.walletprovider

import at.asitplus.openid.ClientNonceResponse
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.encodeToAsn1Primitive
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequestAttribute
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.extensions.toView
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants.CLIENT_ATTESTATION_POP_JWT
import at.asitplus.wallet.lib.jws.JwsHeaderNone
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.walletprovider.data.InstanceAttestationRequest
import at.asitplus.walletprovider.data.KeyAttestationRequest
import at.asitplus.walletprovider.service.network.HttpService
import at.asitplus.walletprovider.service.storage.TokenStoreService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** The wallet API over HTTP, as the wallet app and verifiers use it. */
class ApiEndpointsTest : ProviderTestSupport() {

    @Autowired
    lateinit var httpService: HttpService

    private val period get() = configData.status.fixedTimePeriod

    /** CSR of the instance key; its signature is not checked by the mocked attestation verification */
    private fun csrFor(key: KeyMaterial) = Pkcs10CertificationRequest(
        tbsCsr = TbsCertificationRequest(
            subjectName = listOf(), publicKey = key.publicKey, listOf(), attributes = listOf(
                Pkcs10CertificationRequestAttribute(
                    oid = ObjectIdentifier(oid = configData.provider.solutionOid), listOf(
                        joseCompliantSerializer.encodeToString(InstanceAttestationRequest("1.0", 1.days))
                            .encodeToAsn1Primitive(),
                    )
                )
            )
        ),
        signatureAlgorithm = X509SignatureAlgorithm.RS256,
        rawSignature = "".encodeToAsn1Primitive()
    )

    /** How many status list indices [service] has handed out */
    private fun issuedIndices(service: TokenStoreService) = service.tokenStore.exportData()[period]?.first ?: 0

    /** The status list index of a `status` claim, i.e. `{"status_list": {"idx": …, "uri": …}}` */
    private fun JsonObject.statusListIndex(): Long =
        getValue("status_list").jsonObject.getValue("idx").jsonPrimitive.long

    /** The instance attestation for [key] in its compact serialization, as returned by the endpoint */
    private fun requestInstanceAttestationJws(key: KeyMaterial): String =
        mockMvc.perform(
            post(configData.endpoint.instanceAttestation)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(csrFor(key).encodeToDer())
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

    private fun requestInstanceAttestation(key: KeyMaterial): JwsCompactTyped<JsonWebToken> =
        JwsCompactTyped<JsonWebToken>(requestInstanceAttestationJws(key))

    private fun requestNonce(): String =
        mockMvc.perform(get(configData.endpoint.nonce))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
            .let { joseCompliantSerializer.decodeFromString<ClientNonceResponse>(it).clientNonce }

    private fun keyAttestationRequest(instanceKey: KeyMaterial, attestedKey: KeyMaterial): String = runBlocking {
        val instanceAttestation = requestInstanceAttestationJws(instanceKey)
        val proof = SignJwt<JsonWebToken>(instanceKey, headerModifier = JwsHeaderNone())(
            CLIENT_ATTESTATION_POP_JWT,
            JsonWebToken(
                audience = null,
                nonce = requestNonce(),
                issuedAt = Clock.System.now() - 5.minutes,
                expiration = Clock.System.now() + 60.minutes
            ),
            JsonWebToken.serializer(),
        ).getOrThrow()
        joseCompliantSerializer.encodeToString(
            KeyAttestationRequest(
                token = instanceAttestation,
                proof = proof.jws.toString(),
                keys = listOf(attestedKey.jsonWebKey),
                keyStorage = setOf("iso_18045_high"),
                userAuthentication = setOf("iso_18045_high"),
                nonce = null,
                preferredKeyStorageStatusPeriod = 31.days,
                supportedAlgorithms = setOf()
            )
        )
    }

    @Test
    fun `instance attestation is issued for a DER encoded CSR`() {
        val instanceKey = EphemeralKeyWithoutCert()
        val instanceAttestation = requestInstanceAttestation(instanceKey)

        assertEquals(instanceKey.jsonWebKey, instanceAttestation.payload.confirmationClaim?.jsonWebKey)
        assertEquals(configData.provider.solutionId, instanceAttestation.payload.walletName)
        val index = assertNotNull(instanceAttestation.payload.clientStatus).status.statusListIndex()
        assertEquals(0, httpService.clientStatuses()[index.toUInt()])
    }

    @Test
    fun `key attestation is issued once per nonce`() {
        val attestedKey = EphemeralKeyWithoutCert()
        val request = keyAttestationRequest(EphemeralKeyWithoutCert(), attestedKey)
        val keyAttestation = mockMvc.perform(
            post(configData.endpoint.keyAttestation).contentType(MediaType.APPLICATION_JSON).content(request)
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
            .let { JwsCompactTyped<KeyAttestationJwt>(it) }

        assertEquals(listOf(attestedKey.jsonWebKey), keyAttestation.payload.attestedKeys)
        val index = assertNotNull(keyAttestation.payload.keyStorageStatus).status.statusListIndex()
        assertEquals(0, httpService.keyStorageStatuses()[index.toUInt()])

        // The nonce of the proof has been used up; the rejected request does not use up an index
        val issued = issuedIndices(httpService.keyStorageTokenStoreService)
        mockMvc.perform(
            post(configData.endpoint.keyAttestation).contentType(MediaType.APPLICATION_JSON).content(request)
        ).andExpect(status().isBadRequest)
        assertEquals(issued, issuedIndices(httpService.keyStorageTokenStoreService))
    }

    @Test
    fun `malformed requests are refused`() {
        val issuedClient = issuedIndices(httpService.clientTokenStoreService)
        val issuedKeyStorage = issuedIndices(httpService.keyStorageTokenStoreService)
        mockMvc.perform(
            post(configData.endpoint.instanceAttestation)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(byteArrayOf(1, 2, 3))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(post(configData.endpoint.keyAttestation).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)

        // Rejected requests do not use up status list indices
        assertEquals(issuedClient, issuedIndices(httpService.clientTokenStoreService))
        assertEquals(issuedKeyStorage, issuedIndices(httpService.keyStorageTokenStoreService))
    }

    @Test
    fun `status lists of other time periods are not found`() {
        val otherPeriods = listOf(period + 1, 0, -1, Int.MAX_VALUE)
        for (endpoint in listOf(configData.endpoint.clientStatus, configData.endpoint.keyStorageStatus)) {
            for (otherPeriod in otherPeriods) {
                mockMvc.perform(get("$endpoint/$otherPeriod")).andExpect(status().isNotFound)
            }
        }
        // No status lists were created for them
        assertTrue(httpService.clientTokenStoreService.tokenStore.exportData().keys.none { it in otherPeriods })
        assertTrue(httpService.keyStorageTokenStoreService.tokenStore.exportData().keys.none { it in otherPeriods })
    }

    @Test
    fun `revoked instance attestation shows up in the published status list`() {
        val instanceAttestation = requestInstanceAttestation(EphemeralKeyWithoutCert())
        val index = assertNotNull(instanceAttestation.payload.clientStatus).status.statusListIndex()

        mockMvc.perform(
            post("/admin/client-status").with(user("admin").roles("ADMIN")).with(csrf()).param("status-$index", "1")
        ).andExpect(status().is3xxRedirection)

        val response = mockMvc.perform(get("${configData.endpoint.clientStatus}/$period"))
            .andExpect(status().isOk)
            .andReturn().response
        assertEquals("application/statuslist+jwt", response.contentType)
        val token = JwsCompactTyped<StatusListTokenPayload>(response.contentAsString)
        val statusList = (token.payload.revocationList as StatusList).toView()
        assertEquals(TokenStatus.Invalid, statusList[index])
    }
}
