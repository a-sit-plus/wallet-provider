package at.asitplus.walletprovider

import at.asitplus.walletprovider.injection.inject
import at.asitplus.walletprovider.injection.injectDependencies
import at.asitplus.walletprovider.service.WalletProviderMain
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

fun main(args: Array<String>): Unit = EngineMain.main(args)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

fun Application.module() {
    injectDependencies(environment.config)
    moduleServer()
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Application.moduleServer() {
    inject<WalletProviderMain>().let { provider ->
        routing {
            get(provider.configData.endpoint.root) {
                provider.httpService.handleRootRequest().onSuccess {
                    call.respondHtmlTemplate(it) {}
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.update) {
                provider.httpService.handleUpdateRequest(call.receiveParameters()).onSuccess {
                    call.respondRedirect(provider.configData.endpoint.root)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get("${provider.configData.endpoint.keyStorageStatus}/{period}") {
                // TODO: Add period handling
                provider.httpService.handleKeyStorageStatusRequest().onSuccess {
                    call.respondText(text = it.serialize())
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get("${provider.configData.endpoint.clientStatus}/{period}") {
                // TODO: Add period handling
                provider.httpService.handleClientStatusRequest().onSuccess {
                    call.respondText(text = it.serialize())
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.challenge) {
                provider.httpService.handleChallengeRequest().onSuccess {
                    call.respondText(it, contentType = ContentType.Application.Json)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.nonce) {
                provider.httpService.handleNonceRequest().onSuccess {
                    call.respondText(it, contentType = ContentType.Application.Json)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.instanceAttestation) {
                provider.httpService.handleInstanceRequest(call.receive<ByteArray>()).onSuccess {
                    call.respondText(it.serialize())
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.keyAttestation) {
                provider.httpService.handleKeyAttestationRequest(call.receive<String>()).onSuccess {
                    call.respondText(it.serialize())
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
        }
    }
}




