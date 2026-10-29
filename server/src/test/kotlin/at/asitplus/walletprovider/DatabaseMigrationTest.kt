package at.asitplus.walletprovider

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The schema is created by the Flyway migrations (on H2 in PostgreSQL mode in the tests); that the database service can
 * read and write the tables is covered by [StatusPersistenceTest].
 */
class DatabaseMigrationTest : ProviderTestSupport() {

    @Autowired
    lateinit var flyway: Flyway

    @Test
    fun `all migrations are applied on startup`() {
        val info = flyway.info()
        // Without the entry Flyway records for creating the schema `wallet_provider`, which has no version
        val migrations = info.applied().filter { it.version != null }
        assertEquals(listOf("1", "2"), migrations.map { it.version.version })
        assertEquals(listOf(MigrationState.SUCCESS, MigrationState.SUCCESS), migrations.map { it.state })
        assertEquals(0, info.pending().size)
    }
}
