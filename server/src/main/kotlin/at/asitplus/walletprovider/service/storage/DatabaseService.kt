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
            SchemaUtils.create(KeyStorageStatusLists)
            SchemaUtils.create(ClientStatusLists)
        }
    }

    object KeyStorageStatusLists : Table("key_storage_status") {
        val timePeriod = integer("time_period")
        val data = binary("data")
        val counter = integer("counter")
        val statusBitSize = varchar("status_bit_size", 50)

        override val primaryKey = PrimaryKey(timePeriod)
    }

    object ClientStatusLists : Table("client_status_lists") {
        val timePeriod = integer("time_period")
        val data = binary("data")
        val counter = integer("counter")
        val statusBitSize = varchar("status_bit_size", 50)

        override val primaryKey = PrimaryKey(timePeriod)
    }

    fun loadKeyStorageStatusLists() = runCatching {
        transaction {
            KeyStorageStatusLists.selectAll().associate {
                it[KeyStorageStatusLists.timePeriod] to (it[KeyStorageStatusLists.counter] to StatusListView(
                    uncompressed = it[KeyStorageStatusLists.data],
                    statusBitSize = TokenStatusBitSize.valueOf(it[KeyStorageStatusLists.statusBitSize]),
                ))
            }
        }
    }

    fun loadClientStatusLists() = runCatching {
        transaction {
            ClientStatusLists.selectAll().associate {
                it[ClientStatusLists.timePeriod] to (it[ClientStatusLists.counter] to StatusListView(
                    uncompressed = it[ClientStatusLists.data],
                    statusBitSize = TokenStatusBitSize.valueOf(it[ClientStatusLists.statusBitSize]),
                ))
            }
        }
    }


    fun saveKeyStorageStatusLists(data: Map<Int, Pair<Int, StatusListView>>) = runCatching {
        data.forEach { timePeriod, (counter, statusListView) ->
            transaction {
                KeyStorageStatusLists.upsert {
                    it[KeyStorageStatusLists.timePeriod] = timePeriod
                    it[KeyStorageStatusLists.data] = statusListView.uncompressed
                    it[KeyStorageStatusLists.counter] = counter
                    it[statusBitSize] = statusListView.statusBitSize.name
                }
            }
        }
    }

    fun saveClientStatusLists(data: Map<Int, Pair<Int, StatusListView>>) = runCatching {
        data.forEach { timePeriod, (counter, statusListView) ->
            transaction {
                ClientStatusLists.upsert {
                    it[ClientStatusLists.timePeriod] = timePeriod
                    it[ClientStatusLists.data] = statusListView.uncompressed
                    it[ClientStatusLists.counter] = counter
                    it[statusBitSize] = statusListView.statusBitSize.name
                }
            }
        }
    }

}