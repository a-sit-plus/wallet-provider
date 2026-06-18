package at.asitplus.walletprovider

import at.asitplus.walletprovider.injection.inject
import at.asitplus.walletprovider.injection.injectDependencies
import at.asitplus.walletprovider.service.WalletProviderMain
import io.github.aakira.napier.Napier
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
            get("/") {
                provider.httpService.handleRootPageRequest().onSuccess {
                    call.respondHtmlTemplate(it) {}
                }.onFailure {
                    Napier.e("handleRootPageRequest failed with $it", it)
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.viewKeyStorageStatus) {
                provider.httpService.handleViewKeyStorageStatusRequest().onSuccess {
                    call.respondHtmlTemplate(it) {}
                }.onFailure {
                    Napier.e("handleViewKeyStorageStatusRequest failed with $it", it)
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.updateKeyStorageStatus) {
                provider.httpService.handleUpdateKeyStorageStatusRequest(call.receiveParameters()).onSuccess {
                    call.respondRedirect(provider.configData.endpoint.viewKeyStorageStatus)
                }.onFailure {
                    Napier.e("handleUpdateKeyStorageStatusRequest failed with $it", it)
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.viewClientStatus) {
                provider.httpService.handleViewClientStatusRequest().onSuccess {
                    call.respondHtmlTemplate(it) {}
                }.onFailure {
                    Napier.e("handleViewClientStatusRequest failed with $it", it)
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.updateClientStatus) {
                provider.httpService.handleUpdateClientStatusRequest(call.receiveParameters()).onSuccess {
                    call.respondRedirect(provider.configData.endpoint.viewClientStatus)
                }.onFailure {
                    Napier.e("handleUpdateClientStatusRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get("${provider.configData.endpoint.keyStorageStatus}/{period}") {
                provider.httpService.handleKeyStorageStatusRequest(params = call.parameters).onSuccess {
                    call.respondText(text = it.jws.toString())
                }.onFailure {
                    Napier.e("handleKeyStorageStatusRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get("${provider.configData.endpoint.clientStatus}/{period}") {
                provider.httpService.handleClientStatusRequest(params = call.parameters).onSuccess {
                    call.respondText(text = it.jws.toString())
                }.onFailure {
                    Napier.e("handleClientStatusRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.challenge) {
                provider.httpService.handleChallengeRequest().onSuccess {
                    call.respondText(it, contentType = ContentType.Application.Json)
                }.onFailure {
                    Napier.e("handleChallengeRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            get(provider.configData.endpoint.nonce) {
                provider.httpService.handleNonceRequest().onSuccess {
                    call.respondText(it, contentType = ContentType.Application.Json)
                }.onFailure {
                    Napier.e("handleNonceRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.instanceAttestation) {
                provider.httpService.handleInstanceRequest(call.receive<ByteArray>()).onSuccess {
                    call.respondText(text = it.jws.toString())
                }.onFailure {
                    Napier.e("handleInstanceRequest failed with $it")
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
            post(provider.configData.endpoint.keyAttestation) {
                provider.httpService.handleKeyAttestationRequest(call.receive<String>()).onSuccess {
                    call.respondText(text = it.jws.toString())
                }.onFailure {
                    Napier.e("handleKeyAttestationRequest failed with $it", it)
                    call.respond(HttpStatusCode.BadRequest, it.toString())
                }
            }
        }
    }
}




