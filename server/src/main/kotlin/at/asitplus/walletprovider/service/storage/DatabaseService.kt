package at.asitplus.walletprovider.service.storage

import at.asitplus.catchingUnwrapped
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.support.TransactionTemplate

/**
 * Stores the status lists, one row per time period with its index counter, in the provider's schema
 * (`spring.flyway.default-schema`); the tables are created by Flyway, see `db/walletprovider`.
 */
class DatabaseService(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    schema: String,
) {
    // Quoted, so that it is the schema exactly as Flyway creates it (lowercase on H2 as well as on PostgreSQL).
    private val keyStorageTable = "\"$schema\".key_storage_status_lists"
    private val clientTable = "\"$schema\".client_status_lists"

    fun loadKeyStorageStatusLists() = catchingUnwrapped { load(keyStorageTable) }

    fun loadClientStatusLists() = catchingUnwrapped { load(clientTable) }

    fun saveKeyStorageStatusLists(data: Map<Int, Pair<Int, StatusListView>>) = catchingUnwrapped {
        save(keyStorageTable, data)
    }

    fun saveClientStatusLists(data: Map<Int, Pair<Int, StatusListView>>) = catchingUnwrapped {
        save(clientTable, data)
    }

    /** Status lists with their index counters, by time period */
    private fun load(table: String): Map<Int, Pair<Int, StatusListView>> =
        jdbc.query("SELECT time_period, data, counter, status_bit_size FROM $table", RowMapper { rs, _ ->
            rs.getInt("time_period") to (rs.getInt("counter") to StatusListView(
                uncompressed = rs.getBytes("data"),
                statusBitSize = TokenStatusBitSize.valueOf(rs.getString("status_bit_size")),
            ))
        }).toMap()

    /** Updates the rows of the time periods in [data], or inserts them if there are none yet */
    private fun save(table: String, data: Map<Int, Pair<Int, StatusListView>>) {
        transactions.executeWithoutResult {
            data.forEach { (timePeriod, entry) ->
                val (counter, statusListView) = entry
                val bitSize = statusListView.statusBitSize.name
                val updated = jdbc.update(
                    "UPDATE $table SET data = ?, counter = ?, status_bit_size = ? WHERE time_period = ?",
                    statusListView.uncompressed, counter, bitSize, timePeriod
                )
                if (updated == 0) {
                    jdbc.update(
                        "INSERT INTO $table (time_period, data, counter, status_bit_size) VALUES (?, ?, ?, ?)",
                        timePeriod, statusListView.uncompressed, counter, bitSize
                    )
                }
            }
        }
    }
}
