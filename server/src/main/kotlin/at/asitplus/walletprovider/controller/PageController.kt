package at.asitplus.walletprovider.controller

import at.asitplus.walletprovider.config.SecurityConfig
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.network.HttpService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Duration
import kotlin.time.toKotlinDuration

data class FlashMessage(val category: String, val text: String)

/** Values of the Token Status List spec, as offered on the admin pages. */
enum class StatusOption(val value: Int, val label: String, val badgeClass: String) {
    VALID(0, "Valid", "bg-success"),
    INVALID(1, "Invalid", "bg-danger"),
    SUSPENDED(2, "Suspended", "bg-warning text-dark"),
    APPLICATION_SPECIFIC(3, "Application specific", "bg-secondary");

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value }
    }
}

/** [index] is a [Long], as templates cannot read Kotlin's unsigned types (inline classes) */
data class StatusRow(val index: Long, val status: StatusOption)

data class EndpointRow(val method: String, val url: String, val description: String)

/** The login state and the wallet name for the layout of every page. */
@ControllerAdvice
class LayoutModelAdvice(
    private val configData: ConfigData,
) {
    @ModelAttribute("adminLoggedIn")
    fun adminLoggedIn(request: HttpServletRequest): Boolean = request.isUserInRole("ADMIN")

    @ModelAttribute("walletName")
    fun walletName(): String = configData.provider.solutionId
}

@Controller
class PageController(
    private val configData: ConfigData,
    private val httpService: HttpService,
) {

    private fun url(vararg paths: String) = configData.buildEndpointString(paths.toList())

    private fun Duration.display() = toKotlinDuration().toString()

    @GetMapping("/")
    fun index(model: Model): String {
        val endpoint = configData.endpoint
        val period = configData.status.fixedTimePeriod.toString()
        model.addAttribute("provider", configData.provider)
        model.addAttribute(
            "endpoints", listOf(
                EndpointRow("GET", url(endpoint.challenge), "Challenge for the attestation of the instance key"),
                EndpointRow("POST", url(endpoint.instanceAttestation), "CSR with attestation, returns the Wallet Instance Attestation"),
                EndpointRow("GET", url(endpoint.nonce), "Nonce for the proof of possession of the Wallet Instance Attestation"),
                EndpointRow("POST", url(endpoint.keyAttestation), "Key attestation request, returns the Key Attestation"),
                EndpointRow("GET", url(endpoint.clientStatus, period), "Status list of the Wallet Instance Attestations"),
                EndpointRow("GET", url(endpoint.keyStorageStatus, period), "Status list of the Key Attestations"),
            )
        )
        with(configData.attestation) {
            model.addAttribute("instanceLifetime", instanceAttestation.lifetime.display())
            model.addAttribute("instanceMaintenance", instanceAttestation.maintenance.display())
            model.addAttribute("keyLifetime", keyAttestation.lifetime.display())
            model.addAttribute("keyMaintenance", keyAttestation.maintenance.display())
        }
        return "index"
    }

    @GetMapping("/admin/login")
    fun login(): String = "admin_login"

    @GetMapping("/admin", "/admin/")
    fun admin(): String = "redirect:${SecurityConfig.ADMIN_HOME}"

    @GetMapping(SecurityConfig.ADMIN_HOME)
    fun keyStorageStatus(model: Model): String = statusPage(
        model,
        view = "key-storage",
        action = SecurityConfig.ADMIN_HOME,
        title = "Key Attestations",
        statusListUrl = url(configData.endpoint.keyStorageStatus, configData.status.fixedTimePeriod.toString()),
        statuses = httpService.keyStorageStatuses(),
    )

    @PostMapping(SecurityConfig.ADMIN_HOME)
    fun updateKeyStorageStatus(@RequestParam params: Map<String, String>, redirect: RedirectAttributes): String {
        val statuses = parseStatuses(params, httpService.keyStorageStatuses())
        httpService.updateKeyStorageStatuses(statuses).getOrThrow()
        redirect.addFlashAttribute("flashMessages", listOf(updatedMessage(statuses)))
        return "redirect:${SecurityConfig.ADMIN_HOME}"
    }

    @GetMapping("/admin/client-status")
    fun clientStatus(model: Model): String = statusPage(
        model,
        view = "client",
        action = "/admin/client-status",
        title = "Wallet Instance Attestations",
        statusListUrl = url(configData.endpoint.clientStatus, configData.status.fixedTimePeriod.toString()),
        statuses = httpService.clientStatuses(),
    )

    @PostMapping("/admin/client-status")
    fun updateClientStatus(@RequestParam params: Map<String, String>, redirect: RedirectAttributes): String {
        val statuses = parseStatuses(params, httpService.clientStatuses())
        httpService.updateClientStatuses(statuses).getOrThrow()
        redirect.addFlashAttribute("flashMessages", listOf(updatedMessage(statuses)))
        return "redirect:/admin/client-status"
    }

    private fun statusPage(
        model: Model,
        view: String,
        action: String,
        title: String,
        statusListUrl: String,
        statuses: Map<UInt, Int>,
    ): String {
        val rows = statuses.map { (index, value) ->
            StatusRow(index.toLong(), StatusOption.of(value) ?: StatusOption.APPLICATION_SPECIFIC)
        }.sortedBy { it.index }
        model.addAttribute("view", view)
        model.addAttribute("action", action)
        model.addAttribute("title", title)
        model.addAttribute("statusListUrl", statusListUrl)
        model.addAttribute("rows", rows)
        model.addAttribute("options", StatusOption.entries)
        model.addAttribute("counts", StatusOption.entries.associateWith { option -> rows.count { it.status == option } })
        return "admin_status"
    }

    /**
     * The changed statuses from the form fields `status-<index>`; only indices of issued attestations and status
     * values of [StatusOption] are accepted (else `400 Bad Request`).
     */
    private fun parseStatuses(params: Map<String, String>, current: Map<UInt, Int>): Map<ULong, UInt> =
        params.filterKeys { it.startsWith("status-") }.mapNotNull { (key, value) ->
            val index = key.removePrefix("status-").toUIntOrNull()?.takeIf { it in current }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown index in $key")
            val status = value.toIntOrNull()?.let { StatusOption.of(it) }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status $value")
            if (current[index] == status.value) null else index.toULong() to status.value.toUInt()
        }.toMap()

    private fun updatedMessage(statuses: Map<ULong, UInt>) = when (statuses.size) {
        0 -> FlashMessage("info", "No status changed")
        1 -> FlashMessage("success", "Updated the status of 1 attestation")
        else -> FlashMessage("success", "Updated the status of ${statuses.size} attestations")
    }
}
