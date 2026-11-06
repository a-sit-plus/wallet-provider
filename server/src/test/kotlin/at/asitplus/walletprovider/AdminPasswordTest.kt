package at.asitplus.walletprovider

import at.asitplus.walletprovider.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl

private fun ProviderTestSupport.login(password: String): ResultActions =
    mockMvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", password))

/** The admin password may be configured as a `{bcrypt}` hash instead of plaintext. */
class HashedAdminPasswordTest : ProviderTestSupport() {

    companion object {
        private const val PASSWORD = "hashed-admin-pass"
        private val HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD)!!

        @JvmStatic
        @DynamicPropertySource
        fun registerHashedAdminPassword(registry: DynamicPropertyRegistry) {
            registry.add("admin.password") { HASH }
        }
    }

    @Test
    fun `admin logs in with the password whose hash is configured`() {
        login(PASSWORD).andExpect(redirectedUrl(SecurityConfig.ADMIN_HOME))
    }

    @Test
    fun `the hash itself is not accepted as password`() {
        login(HASH).andExpect(redirectedUrl("/admin/login?error"))
    }
}

/** Without a configured password nobody can log in, not even with an empty password. */
@TestPropertySource(properties = ["admin.password="])
class DisabledAdminLoginTest : ProviderTestSupport() {

    @Test
    fun `no password is accepted`() {
        for (password in listOf("", "test-admin-password", "admin")) {
            login(password).andExpect(redirectedUrl("/admin/login?error"))
        }
    }
}
