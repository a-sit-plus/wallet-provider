package at.asitplus.walletprovider.service.network

import at.asitplus.catchingUnwrapped
import at.asitplus.openid.ClientNonceResponse
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.KeyAttestationRequest
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.storage.ClientTokenStoreService
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import at.asitplus.walletprovider.service.storage.TokenStoreService
import at.asitplus.walletprovider.service.storage.getMap
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

class HttpService(
    val configData: ConfigData,
    val clientTokenStoreService: ClientTokenStoreService,
    val keyStorageTokenStoreService: KeyStorageTokenStoreService,
    val attestationService: AttestationService,
) {
    suspend fun handleKeyStorageStatusRequest(period: Int) = catchingUnwrapped {
        keyStorageTokenStoreService.statusListAgent.issueStatusListJwt(
            kind = RevocationList.Kind.STATUS_LIST,
            timePeriod = period
        )
    }

    suspend fun handleClientStatusRequest(period: Int) = catchingUnwrapped {
        clientTokenStoreService.statusListAgent.issueStatusListJwt(
            kind = RevocationList.Kind.STATUS_LIST,
            timePeriod = period
        )
    }

    /** Status values of all key attestations of the current time period, by status list index */
    fun keyStorageStatuses(): Map<UInt, Int> = keyStorageTokenStoreService.issuedStatuses()

    /** Status values of all instance attestations of the current time period, by status list index */
    fun clientStatuses(): Map<UInt, Int> = clientTokenStoreService.issuedStatuses()

    /** Statuses of the indices handed out so far, without the padding of the last byte of the status list */
    private fun TokenStoreService.issuedStatuses(): Map<UInt, Int> {
        val period = configData.status.fixedTimePeriod
        val issued = tokenStore.exportData()[period]?.first ?: 0
        return tokenStore.getStatusListView(period).getMap().filterKeys { it < issued.toUInt() }
    }

    fun updateKeyStorageStatuses(statuses: Map<ULong, UInt>) = keyStorageTokenStoreService.updateStatuses(statuses)

    fun updateClientStatuses(statuses: Map<ULong, UInt>) = clientTokenStoreService.updateStatuses(statuses)

    /** Sets the [statuses] by status list index and writes them to the database right away, not only on the next export */
    private fun TokenStoreService.updateStatuses(statuses: Map<ULong, UInt>) = catchingUnwrapped {
        statuses.forEach { (index, status) ->
            tokenStore.setStatus(configData.status.fixedTimePeriod, index, TokenStatus(status))
        }
        saveToDatabase(tokenStore.exportData()).getOrThrow()
    }

    suspend fun handleChallengeRequest() = catchingUnwrapped {
        Napier.i("Issuing Challenge", tag = "HttpService")
        attestationService.issueChallenge()
    }

    suspend fun handleNonceRequest() = catchingUnwrapped {
        Napier.i("Issuing Nonce", tag = "HttpService")

        attestationService.getNonce().let {
            Json.encodeToString(ClientNonceResponse(it))
        }
    }

    suspend fun handleInstanceRequest(request: ByteArray) = catchingUnwrapped {
        Napier.i("Start Instance Attestation", tag = "HttpService")
        val csr = Pkcs10CertificationRequest.decodeFromDer(request)
        attestationService.buildInstanceAttestation(csr) { clientTokenStoreService.issueIndex() }.getOrThrow()
    }

    suspend fun handleKeyAttestationRequest(request: String) = catchingUnwrapped {
        Napier.i("Start KeyAttestation", tag = "HttpService")
        val keyAttestationRequest = joseCompliantSerializer.decodeFromString<KeyAttestationRequest>(request)
        attestationService.buildKeyAttestation(keyAttestationRequest) { keyStorageTokenStoreService.issueIndex() }
            .getOrThrow()
    }

    /**
     * Hands out the next status list index, with status valid; only for attestations that passed verification, so a
     * rejected request does not use up an index.
     */
    private fun TokenStoreService.issueIndex(): Int = configData.status.fixedTimePeriod.let { period ->
        tokenStore.getNextFreeIndex(period).also { tokenStore.setStatus(period, it.toULong(), TokenStatus.Valid) }
    }
}
