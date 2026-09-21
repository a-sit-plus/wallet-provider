package at.asitplus.walletprovider.service.storage

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.catchingUnwrapped
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.Identifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.IdentifierInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import kotlin.uuid.Uuid

data class TokenMapEntry(
    var counter: Int, val map: MutableMap<Int, TokenStatus>,
)

class InMemoryTokenStore(
    val tokenStatusBitSize: TokenStatusBitSize = TokenStatusBitSize.TWO,
) : ReferencedTokenStore {

    private val tokenMap = mutableMapOf<Int, TokenMapEntry>()

    fun getNextFreeIndex(timePeriod: Int): Int {
        val entry = tokenMap.getOrPut(timePeriod) {
            TokenMapEntry(0, mutableMapOf())
        }
        return entry.counter.also {
            entry.counter++
        }
    }

    fun importData(data: Map<Int, Pair<Int, StatusListView>>) {
        data.forEach { timePeriod, (counter, statusListView) ->
            tokenMap[timePeriod] = TokenMapEntry(counter, statusListView.getMap().map {
                it.key.toInt() to TokenStatus(it.value.toUInt())
            }.toMap().toMutableMap())
        }
    }

    fun exportData() =
        tokenMap.mapNotNull { (timePeriod, _) ->
            val counter = tokenMap[timePeriod]?.counter ?: return@mapNotNull null
            timePeriod to (counter to getStatusListView(timePeriod))
        }.toMap()

    override suspend fun storeReferencedToken(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<ReferencedTokenStore.StoredCredentialReference> = catching {
        ReferencedTokenStore.StoredCredentialReference(
            id = Uuid.random().toString(),
            timePeriod = timePeriod,
            statusListIndex = getNextFreeIndex(timePeriod).toULong(),
        )
    }


    override fun getStatusListView(timePeriod: Int): StatusListView {
        val timePeriodStatusMap = tokenMap.getOrPut(timePeriod, { TokenMapEntry(0, mutableMapOf()) })
        val highestIndex = timePeriodStatusMap.map.keys.maxOrNull() ?: 0

        val tokenStatusList = (0U..highestIndex.toUInt()).map {
            timePeriodStatusMap.map.getOrDefault(it.toInt(), TokenStatus.Valid)
        }

        return StatusListView.fromTokenStatuses(
            tokenStatusList,
            statusBitSize = tokenStatusBitSize,
        )
    }

    override fun getRawIdentifierList(timePeriod: Int): Map<Identifier, IdentifierInfo> {
        TODO("Not yet implemented")
    }

    override fun setStatus(
        timePeriod: Int, index: ULong, status: TokenStatus,
    ): Boolean {
        if (status.value > tokenStatusBitSize.maxValue) {
            throw IllegalStateException("Token store only accepts token statuses of bitlength `${tokenStatusBitSize.value}`.")
        }
        val entry = tokenMap.getOrPut(timePeriod) { TokenMapEntry(0, mutableMapOf()) }
        entry.map[index.toInt()] = status
        return true
    }

    override fun revokeIdentifier(timePeriod: Int, identifier: ByteArray): Boolean {
        TODO("Not yet implemented")
    }
}

fun ReferencedTokenStore.getMap(timePeriod: Int): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    val timePeriodMap = this.getMap(timePeriod)
    val size = timePeriodMap.keys.size

    (0..size).forEach { index ->
        catchingUnwrapped { timePeriodMap[index] }.getOrNull()?.let {
            result[index] = it
        }
    }
    return result.toMap()
}

fun StatusListView.getMap(): Map<UInt, Int> {
    val result = mutableMapOf<UInt, Int>()
    val size = (this.uncompressed.size * 8) / this.statusBitSize.value.toInt()
    (0..size).forEach { index ->
        catchingUnwrapped { this[index.toUInt()] }.getOrNull()?.let {
            result[index.toUInt()] = it.value.toInt()
        }
    }
    return result.toMap()
}