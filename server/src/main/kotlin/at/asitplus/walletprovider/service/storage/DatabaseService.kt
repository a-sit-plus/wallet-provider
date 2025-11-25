package at.asitplus.walletprovider.service.storage

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import at.asitplus.walletprovider.data.ConfigData
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class DatabaseService(config: ConfigData) {
    init {
        Database.connect(
            url = config.database.url, driver = config.database.driver
        )
        transaction {
            SchemaUtils.create(StatusLists)
        }
    }

    object StatusLists : Table("status_lists") {
        val timePeriod = integer("time_period")
        val data = binary("data")
        val counter = integer("counter")
        val statusBitSize = varchar("status_bit_size", 50)

        override val primaryKey = PrimaryKey(timePeriod)
    }

    fun loadStatusLists() = runCatching {
        transaction {
            StatusLists.selectAll().associate {
                it[StatusLists.timePeriod] to (it[StatusLists.counter] to StatusListView(
                    uncompressed = it[StatusLists.data],
                    statusBitSize = TokenStatusBitSize.valueOf(it[StatusLists.statusBitSize]),
                ))
            }
        }
    }


    fun saveStatusLists(data: Map<Int, Pair<Int, StatusListView>>) = runCatching {
        data.forEach { timePeriod, (counter, statusListView) ->
            transaction {
                StatusLists.upsert {
                    it[StatusLists.timePeriod] = timePeriod
                    it[StatusLists.data] = statusListView.uncompressed
                    it[StatusLists.counter] = counter
                    it[statusBitSize] = statusListView.statusBitSize.name
                }
            }
        }
    }

}