package at.asitplus.walletprovider.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Stops the startup if the database is still configured as in the earlier Ktor version (`database.url`,
 * `database.driver`). Those settings are not read anymore, so the provider would otherwise run on its default H2
 * database, without the status lists of the configured one.
 *
 * Runs after the configuration is loaded (including the config server); registered in `META-INF/spring.factories`.
 */
class LegacyDatabaseConfigCheck : EnvironmentPostProcessor {

    companion object {
        val LEGACY_PROPERTIES = listOf("database.url", "database.driver")
    }

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val configured = LEGACY_PROPERTIES.filter { environment.containsProperty(it) }
        check(configured.isEmpty()) {
            "${configured.joinToString()} is not used anymore: configure the database as spring.datasource.url " +
                "(with spring.datasource.username and password); for H2 use MODE=PostgreSQL instead of MODE=MYSQL"
        }
    }
}
