package at.asitplus.walletprovider.service.network

import at.asitplus.openid.ClientNonceResponse
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.data.UnitAttestationRequest
import at.asitplus.walletprovider.data.template.StatusFormTemplate
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import at.asitplus.walletprovider.service.storage.getMap
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.minutes

class HttpService(
    val statusListAgent: StatusListAgent,
    val configData: ConfigData,
    val tokenStore: InMemoryTokenStore,
    val attestationService: AttestationService,
) {
    suspend fun handleStatusRequest() = runCatching {
        Napier.i("Issuing StatusListJwt")
        statusListAgent.issueStatusListJwt(kind = RevocationList.Kind.STATUS_LIST)
    }

    suspend fun handleRootRequest() = runCatching {
        StatusFormTemplate(
            endpoint = configData.endpoint.update,
            data = tokenStore.getStatusListView(configData.status.fixedTimePeriod)
                .getMap()
        )
    }

    suspend fun handleUpdateRequest(params: Parameters) = runCatching {
        params.flattenEntries().forEach {
            tokenStore.setStatus(
                configData.status.fixedTimePeriod,
                it.first.toULong(),
                TokenStatus(it.second.toUInt())
            )
        }
    }

    suspend fun handleChallengeRequest() = runCatching {
        Napier.i("Issuing Challenge")
        attestationService.issueChallenge()
    }

    suspend fun handleNonceRequest() = runCatching {
        Napier.i("Issuing Nonce")

        attestationService.getNonce().let {
            Json.encodeToString(ClientNonceResponse(it))
        }
    }

    suspend fun handleInstanceRequest(request: ByteArray) = runCatching {
        Napier.i("Start Instance Attestation")
        Pkcs10CertificationRequest.decodeFromDer(request).let { csr ->
            attestationService.buildInstanceAttestation(csr).getOrThrow()
        }
    }

    suspend fun handleUnitRequest(request: String) = runCatching {
        Napier.i("Start Unit Attestation")
        val idx = tokenStore.getNextFreeIndex(1) ?: throw Throwable("Unable to get next free index!")
        attestationService.buildUnitAttestation(vckJsonSerializer.decodeFromString<UnitAttestationRequest>(request), idx)
            .getOrThrow()
            .serialize()
            .also {
                tokenStore.setStatus(
                    configData.status.fixedTimePeriod,
                    idx.toULong(),
                    TokenStatus.Valid
                )
            }
    }
}