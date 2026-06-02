package at.asitplus.walletprovider.service.network

import at.asitplus.openid.ClientNonceResponse
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.KeyAttestationRequest
import at.asitplus.walletprovider.data.template.RootPageTemplate
import at.asitplus.walletprovider.data.template.StatusFormTemplate
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.storage.ClientTokenStoreService
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import at.asitplus.walletprovider.service.storage.getMap
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.Json

class HttpService(
    val configData: ConfigData,
    val clientTokenStoreService: ClientTokenStoreService,
    val keyStorageTokenStoreService: KeyStorageTokenStoreService,
    val attestationService: AttestationService,
) {
    fun handleRootPageRequest() = runCatching {
        RootPageTemplate(
            endpointKeyStorageStatus = configData.endpoint.viewKeyStorageStatus,
            endpointClientStatus = configData.endpoint.viewClientStatus
        )
    }

    suspend fun handleKeyStorageStatusRequest(params: Parameters) = runCatching {
        params.parsePeriod().let {
            keyStorageTokenStoreService.statusListAgent.issueStatusListJwt(
                kind = RevocationList.Kind.STATUS_LIST,
                timePeriod = it
            )
        }
    }

    suspend fun handleClientStatusRequest(params: Parameters) = runCatching {
        params.parsePeriod().let {
            clientTokenStoreService.statusListAgent.issueStatusListJwt(
                kind = RevocationList.Kind.STATUS_LIST,
                timePeriod = it
            )
        }
    }

    fun handleViewKeyStorageStatusRequest() = runCatching {
        StatusFormTemplate(
            endpoint = configData.endpoint.updateKeyStorageStatus,
            data = keyStorageTokenStoreService.tokenStore.getStatusListView(configData.status.fixedTimePeriod)
                .getMap()
        )
    }

    fun handleUpdateKeyStorageStatusRequest(params: Parameters) = runCatching {
        params.flattenEntries().forEach {
            keyStorageTokenStoreService.tokenStore.setStatus(
                configData.status.fixedTimePeriod,
                it.first.toULong(),
                TokenStatus(it.second.toUInt())
            )
        }
    }

    fun handleViewClientStatusRequest() = runCatching {
        StatusFormTemplate(
            endpoint = configData.endpoint.updateClientStatus,
            data = clientTokenStoreService.tokenStore.getStatusListView(configData.status.fixedTimePeriod)
                .getMap()
        )
    }

    fun handleUpdateClientStatusRequest(params: Parameters) = runCatching {
        params.flattenEntries().forEach {
            clientTokenStoreService.tokenStore.setStatus(
                configData.status.fixedTimePeriod,
                it.first.toULong(),
                TokenStatus(it.second.toUInt())
            )
        }
    }

    suspend fun handleChallengeRequest() = runCatching {
        Napier.i("Issuing Challenge", tag = "HttpService")
        attestationService.issueChallenge()
    }

    suspend fun handleNonceRequest() = runCatching {
        Napier.i("Issuing Nonce", tag = "HttpService")

        attestationService.getNonce().let {
            Json.encodeToString(ClientNonceResponse(it))
        }
    }

    suspend fun handleInstanceRequest(request: ByteArray) = runCatching {
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

    suspend fun handleKeyAttestationRequest(request: String) = runCatching {
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

    private fun Parameters.parsePeriod(): Int = (this["period"] ?: throw Throwable("No period in URL")).toInt()

}
