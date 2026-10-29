package at.asitplus.walletprovider.controller

import at.asitplus.attestation.supreme.SupremeConfiguration
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.network.HttpService
import io.github.aakira.napier.Napier
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** The endpoints used by the wallet app and by verifiers checking the revocation status. */
@RestController
class ApiController(
    private val configData: ConfigData,
    private val httpService: HttpService,
    supremeConfiguration: SupremeConfiguration,
) {
    /**
     * Largest accepted body of the attestation requests: Warden Supreme's limit for attestation proofs
     * (`supreme.maxAttestationPayloadBytes`, 1 MiB by default), which a key attestation request stays far below
     */
    private val maxRequestBytes = supremeConfiguration.maxAttestationPayloadBytes

    companion object {
        /** Media type of a Status List Token in JWT format, see Token Status List spec */
        val STATUS_LIST_JWT = MediaType("application", "statuslist+jwt")
        private val TEXT_PLAIN_UTF8 = MediaType("text", "plain", Charsets.UTF_8)
    }

    @GetMapping("\${endpoints.keyStorageStatus}/{period}")
    fun keyStorageStatus(@PathVariable period: Int) = ifKnownPeriod(period) {
        httpService.handleKeyStorageStatusRequest(period)
            .respond("handleKeyStorageStatusRequest", STATUS_LIST_JWT) { it.jws.toString().encodeToByteArray() }
    }

    @GetMapping("\${endpoints.clientStatus}/{period}")
    fun clientStatus(@PathVariable period: Int) = ifKnownPeriod(period) {
        httpService.handleClientStatusRequest(period)
            .respond("handleClientStatusRequest", STATUS_LIST_JWT) { it.jws.toString().encodeToByteArray() }
    }

    /**
     * Status lists exist only for the time period the attestations reference; any other one is `404 Not Found`, as the
     * store would otherwise create (and keep) a list for each period requested.
     */
    private fun ifKnownPeriod(period: Int, response: suspend () -> ResponseEntity<Any>): ResponseEntity<Any> =
        if (period == configData.status.fixedTimePeriod) runBlocking { response() } else ResponseEntity.notFound().build()

    @GetMapping("\${endpoints.challenge}")
    fun challenge() = runBlocking {
        httpService.handleChallengeRequest().respond("handleChallengeRequest", MediaType.APPLICATION_JSON) { it }
    }

    @GetMapping("\${endpoints.nonce}")
    fun nonce() = runBlocking {
        httpService.handleNonceRequest().respond("handleNonceRequest", MediaType.APPLICATION_JSON) { it }
    }

    @PostMapping("\${endpoints.instanceAttestation}")
    fun instanceAttestation(request: HttpServletRequest) = withLimitedBody(request) { body ->
        httpService.handleInstanceRequest(body)
            .respond("handleInstanceRequest", TEXT_PLAIN_UTF8) { it.jws.toString() }
    }

    @PostMapping("\${endpoints.keyAttestation}")
    fun keyAttestation(request: HttpServletRequest) = withLimitedBody(request) { body ->
        httpService.handleKeyAttestationRequest(body.decodeToString())
            .respond("handleKeyAttestationRequest", TEXT_PLAIN_UTF8) { it.jws.toString() }
    }

    /**
     * Reads at most [maxRequestBytes] of the request body and passes it to [response]; a larger body is answered with
     * `413 Content Too Large` without reading the rest of it into memory.
     */
    private fun withLimitedBody(
        request: HttpServletRequest,
        response: suspend (body: ByteArray) -> ResponseEntity<Any>,
    ): ResponseEntity<Any> {
        val body = request.takeIf { it.contentLengthLong <= maxRequestBytes }
            ?.inputStream?.readNBytes(maxRequestBytes + 1)
            ?.takeIf { it.size <= maxRequestBytes }
            ?: return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).contentType(TEXT_PLAIN_UTF8)
                .body("Request larger than $maxRequestBytes bytes")
        return runBlocking { response(body) }
    }

    /**
     * The result as response, or `400 Bad Request` with the error. A body given as [ByteArray] is sent without charset
     * parameter, as needed for the status list media type.
     */
    private fun <T> Result<T>.respond(
        handler: String,
        mediaType: MediaType,
        body: (T) -> Any,
    ): ResponseEntity<Any> = fold(
        onSuccess = { ResponseEntity.ok().contentType(mediaType).body(body(it)) },
        onFailure = {
            Napier.e("$handler failed with $it", it)
            ResponseEntity.badRequest().contentType(TEXT_PLAIN_UTF8).body(it.toString())
        }
    )
}
