package at.asitplus.walletprovider.config

import at.asitplus.walletprovider.data.AdminConfigData
import io.github.aakira.napier.Napier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * Two access zones: the `/admin/...` pages for viewing and changing the revocation status of issued attestations,
 * behind a form login of the configured admin, and everything else (start page, wallet API, status lists) open to
 * anyone.
 *
 * The admin pages require Spring Security's CSRF token on every state-changing request (the forms get it via
 * `th:action`), logout included. All pages carry a [CONTENT_SECURITY_POLICY] that only allows this origin's scripts
 * and styles.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val admin: AdminConfigData,
) {

    companion object {
        const val ADMIN_HOME = "/admin/key-storage-status"

        /**
         * Content-Security-Policy of the HTML pages: scripts and styles only from this origin (`static/`, webjars), so
         * an injected inline script does not run. `data:` images are the SVG icons inside Bootstrap's CSS.
         */
        const val CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; " +
            "img-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"

        private val ENCODED_PASSWORD = Regex("""^\{[A-Za-z0-9_.-]+}""")

        /**
         * A configured password: kept as is if it already names its encoding (e.g. `{bcrypt}$2a$…`), otherwise hashed
         * once here, so it is never compared or held in plaintext.
         */
        fun storedPassword(configured: String, passwordEncoder: PasswordEncoder): String =
            if (ENCODED_PASSWORD.containsMatchIn(configured)) configured else passwordEncoder.encode(configured)!!
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /** The configured admin; without a password there is no account, so the admin pages stay closed. */
    @Bean
    fun adminUsers(passwordEncoder: PasswordEncoder): UserDetailsService =
        if (admin.password.isBlank()) {
            Napier.w("No admin.password configured, the admin login is disabled", tag = "SecurityConfig")
            InMemoryUserDetailsManager()
        } else {
            InMemoryUserDetailsManager(
                User.withUsername(admin.username).password(storedPassword(admin.password, passwordEncoder))
                    .roles("ADMIN").build()
            )
        }

    @Bean
    @Order(1)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/admin/**")
            .headers { it.contentSecurityPolicy { csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY) } }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/admin/login").permitAll()
                    .anyRequest().hasRole("ADMIN")
            }
            .formLogin { form ->
                form.loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login")
                    .defaultSuccessUrl(ADMIN_HOME, true)
                    .failureUrl("/admin/login?error")
                    .permitAll()
            }
            .logout {
                // POST only, so the CSRF token is required: another site cannot log the admin out.
                it.logoutRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/admin/logout"),
                )
                    .logoutSuccessUrl("/")
                    .permitAll()
            }
        return http.build()
    }

    @Bean
    @Order(2)
    fun publicSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // The wallet API authenticates through attestations, not sessions, and the pages here change nothing, so no
        // request is checked. The token is still issued for the logout form shown to a logged-in admin.
        http.csrf { it.ignoringRequestMatchers("/**") }
            .headers { it.contentSecurityPolicy { csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY) } }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
