package at.asitplus.walletprovider.data.template

import io.ktor.server.html.*
import kotlinx.html.*

class RootPageTemplate(
    private val endpointKeyStorageStatus: String,
    private val endpointClientStatus: String,
) : Template<HTML> {

    val titleText = "Wallet Provider"

    override fun HTML.apply() {
        head {
            title { +titleText }
        }
        body {
            h1 { +titleText }
            form(action = endpointKeyStorageStatus, method = FormMethod.get) {
                button {
                    type = ButtonType.submit
                    +"KeyStorageStatus view"
                }
            }
            form(action = endpointClientStatus, method = FormMethod.get) {
                button {
                    type = ButtonType.submit
                    +"ClientStatus view"
                }
            }
        }
    }
}
