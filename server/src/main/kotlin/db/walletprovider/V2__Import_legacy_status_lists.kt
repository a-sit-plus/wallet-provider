package db.walletprovider

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

/**
 * Imports the status lists of the provider's earlier Ktor version into the tables of V1, so that revocations and the
 * index counters (which index the next attestation gets) are kept.
 *
 * The Ktor version had Exposed create the tables `key_storage_status` and `client_status_lists` in the default schema
 * of the database (`PUBLIC` on H2, `public` on PostgreSQL), with the same columns as V1. They are left as they are, so
 * that the earlier version can still be started; a database without them (a new installation) is not changed.
 *
 * Found by Flyway in `classpath:db/walletprovider` like the SQL migrations. The statements are the same on H2 and
 * PostgreSQL; the old tables are looked up in `information_schema`, as their case differs (upper case on H2).
 */
@Suppress("ClassName")
class V2__Import_legacy_status_lists : BaseJavaMigration() {

    /** Tables of the Ktor version and the tables of V1 their rows go to */
    private val legacyTables = mapOf(
        "key_storage_status" to "key_storage_status_lists",
        "client_status_lists" to "client_status_lists",
    )

    override fun migrate(context: Context) {
        val schema = context.configuration.defaultSchema
            ?: error("The migrations need the provider's schema as default schema")
        val connection = context.connection
        legacyTables.forEach { (legacyName, table) ->
            val legacyTable = findLegacyTable(connection, legacyName, schema) ?: return@forEach
            connection.createStatement().use {
                it.executeUpdate(
                    """INSERT INTO ${quote(schema)}.$table (time_period, data, counter, status_bit_size)
                        SELECT time_period, data, counter, status_bit_size FROM $legacyTable"""
                )
            }
        }
    }

    /** The quoted name of table [name] outside the provider's [schema], or `null` if there is none */
    private fun findLegacyTable(connection: Connection, name: String, schema: String): String? {
        val found = connection.prepareStatement(
            """SELECT table_schema, table_name FROM information_schema.tables
                WHERE UPPER(table_name) = UPPER(?) AND UPPER(table_schema) <> UPPER(?) AND table_type = 'BASE TABLE'"""
        ).use { query ->
            query.setString(1, name)
            query.setString(2, schema)
            query.executeQuery().use { rs ->
                buildList { while (rs.next()) add("${quote(rs.getString(1))}.${quote(rs.getString(2))}") }
            }
        }
        check(found.size <= 1) { "Table $name of the earlier version found more than once: $found" }
        return found.singleOrNull()
    }

    private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""
}
