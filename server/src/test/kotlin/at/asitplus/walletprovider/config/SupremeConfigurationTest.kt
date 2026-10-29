package at.asitplus.walletprovider.config

import at.asitplus.attestation.fromSpringEnvironment
import at.asitplus.attestation.supreme.SupremeConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals

/** The section `supreme` of `application.yaml`, loaded through Spring as the provider does. */
class SupremeConfigurationTest {

    private fun environmentOf(resource: String) = StandardEnvironment().apply {
        YamlPropertySourceLoader().load(resource, ClassPathResource(resource)).forEach { propertySources.addLast(it) }
    }

    /** The `supreme` section of `application.yaml`, which is its last top-level section, as YAML document */
    private fun supremeSectionOf(resource: String) =
        ClassPathResource(resource).inputStream.use { it.readAllBytes().decodeToString() }
            .substringAfter("\nsupreme:\n")
            .lines()
            .joinToString("\n") { it.removePrefix("  ") }

    @Test
    fun `supreme section loaded through Spring equals the section parsed by Warden Supreme`() {
        val expected = SupremeConfiguration.fromYamlString(supremeSectionOf("application.yaml"))
        val loaded = SupremeConfiguration.fromSpringEnvironment(environmentOf("application.yaml"), "supreme")
        assertEquals(expected.toJsonString(), loaded.toJsonString())
    }

    @Test
    fun `single values of a source with higher precedence override those of application yaml`() {
        val base = SupremeConfiguration.fromSpringEnvironment(environmentOf("application.yaml"), "supreme")
        val environment = environmentOf("application.yaml").apply {
            // e.g. the config server
            propertySources.addFirst(MapPropertySource("override", mapOf("supreme.android.requireStrongBox" to "true")))
        }

        val loaded = SupremeConfiguration.fromSpringEnvironment(environment, "supreme")

        assertEquals(false, base.android?.requireStrongBox)
        assertEquals(true, loaded.android?.requireStrongBox)
        assertEquals(base.android?.applications?.map { it.packageName }, loaded.android?.applications?.map { it.packageName })
        assertEquals(base.ios?.toJsonString(), loaded.ios?.toJsonString())
    }
}
