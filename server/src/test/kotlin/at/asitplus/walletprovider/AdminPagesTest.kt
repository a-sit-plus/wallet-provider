package at.asitplus.walletprovider

import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.walletprovider.config.SecurityConfig
import at.asitplus.walletprovider.service.network.HttpService
import at.asitplus.walletprovider.service.storage.ClientTokenStoreService
import at.asitplus.walletprovider.service.storage.DatabaseService
import at.asitplus.walletprovider.service.storage.KeyStorageTokenStoreService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminPagesTest : ProviderTestSupport() {

    @Autowired
    lateinit var httpService: HttpService

    @Autowired
    lateinit var keyStorageTokenStoreService: KeyStorageTokenStoreService

    @Autowired
    lateinit var clientTokenStoreService: ClientTokenStoreService

    @Autowired
    lateinit var databaseService: DatabaseService

    private val admin = user("admin").roles("ADMIN")

    /** Hands out a new status list index for a key attestation, as issuing one does */
    private fun issueKeyAttestationIndex(): Int {
        val period = configData.status.fixedTimePeriod
        val tokenStore = keyStorageTokenStoreService.tokenStore
        return tokenStore.getNextFreeIndex(period).also { tokenStore.setStatus(period, it.toULong(), TokenStatus.Valid) }
    }

    /** Hands out a new status list index for an instance attestation, as issuing one does */
    private fun issueClientIndex(): Int {
        val period = configData.status.fixedTimePeriod
        val tokenStore = clientTokenStoreService.tokenStore
        return tokenStore.getNextFreeIndex(period).also { tokenStore.setStatus(period, it.toULong(), TokenStatus.Valid) }
    }

    /** Logs in through the login form and returns the session holding the login */
    private fun loginSession(): MockHttpSession =
        mockMvc.perform(
            post("/admin/login").with(csrf()).param("username", "admin").param("password", "test-admin-password")
        )
            .andExpect(redirectedUrl(SecurityConfig.ADMIN_HOME))
            .andReturn().request.getSession(false) as MockHttpSession

    @Test
    fun `start page shows the admin login and no logout`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Admin Login")))
            .andExpect(content().string(not(containsString("Admin Logout"))))
    }

    @Test
    fun `status pages require the admin login`() {
        for (page in listOf(SecurityConfig.ADMIN_HOME, "/admin/client-status")) {
            mockMvc.perform(get(page))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/admin/login"))
        }
    }

    @Test
    fun `admin login with correct credentials redirects to the status page`() {
        mockMvc.perform(
            post("/admin/login").with(csrf()).param("username", "admin").param("password", "test-admin-password")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl(SecurityConfig.ADMIN_HOME))
    }

    @Test
    fun `admin login with wrong credentials is refused`() {
        mockMvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "wrong"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/login?error"))
        mockMvc.perform(get("/admin/login").param("error", ""))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Invalid credentials")))
    }

    @Test
    fun `logged-in admin sees the status pages and the logout`() {
        val index = issueKeyAttestationIndex()
        mockMvc.perform(get(SecurityConfig.ADMIN_HOME).with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Admin Logout")))
            .andExpect(content().string(containsString("name=\"status-$index\"")))
        mockMvc.perform(get("/admin/client-status").with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Wallet Instance Attestations")))
    }

    @Test
    fun `admin revokes a key attestation`() {
        val index = issueKeyAttestationIndex()
        mockMvc.perform(post(SecurityConfig.ADMIN_HOME).with(admin).with(csrf()).param("status-$index", "1"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl(SecurityConfig.ADMIN_HOME))
        assertEquals(1, httpService.keyStorageStatuses()[index.toUInt()])

        // Stored right away, not only on the next scheduled export
        val stored = databaseService.loadKeyStorageStatusLists().getOrThrow()
            .getValue(configData.status.fixedTimePeriod).second
        assertEquals(TokenStatus.Invalid, stored[index.toUInt()])
    }

    @Test
    fun `admin suspends a wallet instance attestation`() {
        val index = issueClientIndex()
        mockMvc.perform(post("/admin/client-status").with(admin).with(csrf()).param("status-$index", "2"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/client-status"))
        assertEquals(2, httpService.clientStatuses()[index.toUInt()])
    }

    @Test
    fun `status update with an unknown status value is refused`() {
        val index = issueKeyAttestationIndex()
        for (value in listOf("4", "-1", "revoked")) {
            mockMvc.perform(post(SecurityConfig.ADMIN_HOME).with(admin).with(csrf()).param("status-$index", value))
                .andExpect(status().isBadRequest)
        }
        assertEquals(0, httpService.keyStorageStatuses()[index.toUInt()])
    }

    @Test
    fun `logout ends the admin session`() {
        val session = loginSession()
        mockMvc.perform(get(SecurityConfig.ADMIN_HOME).session(session)).andExpect(status().isOk)

        mockMvc.perform(post("/admin/logout").with(csrf()).session(session))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
        mockMvc.perform(get(SecurityConfig.ADMIN_HOME).session(session))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/login"))
    }

    @Test
    fun `logout without CSRF token is refused`() {
        val session = loginSession()
        mockMvc.perform(post("/admin/logout").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get(SecurityConfig.ADMIN_HOME).session(session)).andExpect(status().isOk)
    }

    @Test
    fun `status update without CSRF token is refused`() {
        val index = issueKeyAttestationIndex()
        mockMvc.perform(post(SecurityConfig.ADMIN_HOME).with(admin).param("status-$index", "1"))
            .andExpect(status().isForbidden)
        assertEquals(0, httpService.keyStorageStatuses()[index.toUInt()])
    }

    @Test
    fun `status update for an index not handed out is refused`() {
        mockMvc.perform(post(SecurityConfig.ADMIN_HOME).with(admin).with(csrf()).param("status-100000", "1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `pages carry the Content-Security-Policy`() {
        for (page in listOf("/", "/admin/login")) {
            mockMvc.perform(get(page))
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
        }
    }

    @Test
    fun `page assets and webjars referenced by the layout are served`() {
        val page = mockMvc.perform(get("/")).andExpect(status().isOk).andReturn().response.contentAsString
        val assets = Regex("""(?:href|src)="(/(?:webjars|css|js)/[^"]+)"""").findAll(page).map { it.groupValues[1] }.toSet()
        assertTrue(assets.any { it.startsWith("/webjars/") }, "layout references no webjar assets")
        for (asset in assets) {
            val response = mockMvc.perform(get(asset)).andExpect(status().isOk).andReturn().response
            assertTrue(!response.contentType.orEmpty().contains("json"), "$asset served as ${response.contentType}")
        }
    }

    @Test
    fun `templates contain no inline scripts, styles or event handlers, which the policy would block`() {
        val inline = listOf(
            Regex("""<script(?![^>]*\bsrc=)[^>]*>"""),
            Regex("""<style\b"""),
            Regex("""\son[a-z]+\s*="""),
            Regex("""\sstyle\s*="""),
        )
        val templates = Files.walk(Path.of("src/main/resources/templates")).use { paths ->
            paths.filter { it.extension == "html" }.toList()
        }
        assertTrue(templates.isNotEmpty())
        val violations = templates.flatMap { template ->
            val html = template.readText()
            inline.mapNotNull { pattern -> pattern.find(html)?.let { "${template.fileName}: ${it.value}" } }
        }
        assertEquals(emptyList(), violations)
    }
}
