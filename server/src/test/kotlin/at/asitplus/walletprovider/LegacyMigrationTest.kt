package at.asitplus.walletprovider

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.walletprovider.config.LegacyDatabaseConfigCheck
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.InMemoryTokenStore
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The provider's schema and Flyway settings, as in `application.yaml` */
private const val SCHEMA = "wallet_provider"

/**
 * A database of the earlier Ktor version, i.e. with the tables Exposed created in the default schema, is migrated by
 * V2 without losing its status lists. Runs on H2 in PostgreSQL mode, like the other database tests.
 */
class LegacyMigrationTest {

    // A database of its own for each test
    private val dataSource: DataSource =
        DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")

    private val jdbc = JdbcTemplate(dataSource)

    private fun migrate() = Flyway.configure().dataSource(dataSource)
        .schemas(SCHEMA).defaultSchema(SCHEMA)
        .locations("classpath:db/walletprovider").table("flyway_schema_history_wallet_provider")
        .load().migrate()

    private fun databaseService() =
        DatabaseService(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)), SCHEMA)

    /** Creates the table [name] as the earlier version did, in the default schema, with the status lists of [store] */
    private fun createLegacyTable(name: String, store: InMemoryTokenStore) {
        jdbc.execute(
            """CREATE TABLE $name (time_period INT NOT NULL, data VARBINARY NOT NULL, counter INT NOT NULL,
                status_bit_size VARCHAR(50) NOT NULL, CONSTRAINT pk_$name PRIMARY KEY (time_period))"""
        )
        store.exportData().forEach { (timePeriod, entry) ->
            val (counter, view) = entry
            jdbc.update(
                "INSERT INTO $name (time_period, data, counter, status_bit_size) VALUES (?, ?, ?, ?)",
                timePeriod, view.uncompressed, counter, view.statusBitSize.name
            )
        }
    }

    /** Two time periods with handed out indices, some of them revoked or suspended */
    private fun storeWithStatuses(revoked: ULong) = InMemoryTokenStore().apply {
        repeat(7) { getNextFreeIndex(1) }
        repeat(2) { getNextFreeIndex(2) }
        setStatus(1, revoked, TokenStatus.Invalid)
        setStatus(1, 5uL, TokenStatus.Suspended)
        setStatus(2, 1uL, TokenStatus.Invalid)
    }

    private fun Map<Int, Pair<Int, StatusListView>>.counters() = mapValues { it.value.first }

    @Test
    fun `status lists of the earlier version are imported`() {
        val keyStorage = storeWithStatuses(revoked = 2uL)
        val client = storeWithStatuses(revoked = 3uL)
        createLegacyTable("key_storage_status", keyStorage)
        createLegacyTable("client_status_lists", client)

        migrate()

        val service = databaseService()
        assertEquals(keyStorage.exportData(), service.loadKeyStorageStatusLists().getOrThrow())
        assertEquals(client.exportData(), service.loadClientStatusLists().getOrThrow())
        assertEquals(mapOf(1 to 7, 2 to 2), service.loadKeyStorageStatusLists().getOrThrow().counters())

        // The tables of the earlier version are kept
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM key_storage_status", Int::class.javaObjectType))
    }

    @Test
    fun `new database starts without status lists`() {
        migrate()

        val service = databaseService()
        assertTrue(service.loadKeyStorageStatusLists().getOrThrow().isEmpty())
        assertTrue(service.loadClientStatusLists().getOrThrow().isEmpty())
    }

    @Test
    fun `migrated database is read and written by the database service`() {
        createLegacyTable("key_storage_status", storeWithStatuses(revoked = 2uL))
        createLegacyTable("client_status_lists", InMemoryTokenStore())
        migrate()

        val store = InMemoryTokenStore().apply {
            importData(databaseService().loadKeyStorageStatusLists().getOrThrow())
            assertEquals(7, getNextFreeIndex(1), "counter continues after the imported indices")
            setStatus(1, 7uL, TokenStatus.Invalid)
        }
        databaseService().saveKeyStorageStatusLists(store.exportData()).getOrThrow()
        assertEquals(store.exportData(), databaseService().loadKeyStorageStatusLists().getOrThrow())
    }
}

class LegacyDatabaseConfigCheckTest {

    private fun check(properties: Map<String, Any>) = LegacyDatabaseConfigCheck().postProcessEnvironment(
        StandardEnvironment().apply { propertySources.addFirst(MapPropertySource("test", properties)) },
        SpringApplication(),
    )

    @Test
    fun `startup stops with the database settings of the earlier version`() {
        val error = assertThrows<IllegalStateException> {
            check(mapOf("database.url" to "jdbc:postgresql://db/provider", "database.driver" to "org.postgresql.Driver"))
        }
        assertTrue(error.message!!.contains("spring.datasource.url"))
    }

    @Test
    fun `startup continues with the current database settings`() {
        check(mapOf("spring.datasource.url" to "jdbc:postgresql://db/provider", "database.exportInterval" to "PT1M"))
    }
}
