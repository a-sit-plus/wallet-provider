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

    fun updateKeyStorageStatuses(statuses: Map<ULong, UInt>) = catchingUnwrapped {
        statuses.forEach { (index, status) ->
            keyStorageTokenStoreService.tokenStore.setStatus(configData.status.fixedTimePeriod, index, TokenStatus(status))
        }
    }

    fun updateClientStatuses(statuses: Map<ULong, UInt>) = catchingUnwrapped {
        statuses.forEach { (index, status) ->
            clientTokenStoreService.tokenStore.setStatus(configData.status.fixedTimePeriod, index, TokenStatus(status))
        }
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
        val idx = clientTokenStoreService.tokenStore.getNextFreeIndex(configData.status.fixedTimePeriod)
        Pkcs10CertificationRequest.decodeFromDer(request).let { csr ->
            attestationService.buildInstanceAttestation(csr, idx).onSuccess {
                clientTokenStoreService.tokenStore.setStatus(
                    configData.status.fixedTimePeriod,
                    idx.toULong(),
                    TokenStatus.Valid
                )
            }.getOrThrow()
        }
    }

    suspend fun handleKeyAttestationRequest(request: String) = catchingUnwrapped {
        Napier.i("Start KeyAttestation", tag = "HttpService")
        val idx = keyStorageTokenStoreService.tokenStore.getNextFreeIndex(configData.status.fixedTimePeriod)
        attestationService.buildKeyAttestation(
            joseCompliantSerializer.decodeFromString<KeyAttestationRequest>(request),
            idx
        )
            .onSuccess {
                keyStorageTokenStoreService.tokenStore.setStatus(
                    configData.status.fixedTimePeriod,
                    idx.toULong(),
                    TokenStatus.Valid
                )
            }.getOrThrow()
    }
}
