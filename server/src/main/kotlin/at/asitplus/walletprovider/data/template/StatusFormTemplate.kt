package at.asitplus.walletprovider.data.template

import io.ktor.server.html.*
import kotlinx.html.*

class StatusFormTemplate(
    private val endpoint: String,
    private val data: Map<UInt, Int>
) : Template<HTML> {

    val border = "border: 1px solid black; padding: 5px;"

    override fun HTML.apply() {
        body {
            form(action = endpoint, method = FormMethod.post) {
                table {
                    style = "border-collapse: collapse; width: 300px;"
                    tr {
                        th {
                            style = border
                            +"Id"
                        }
                        th {
                            style = border
                            +"Value"
                        }
                    }
                    data.map { it.key.toString() to it.value.toString() }.forEach { (label, value) ->
                        tr {
                            style = border
                            td {
                                style = border
                                +label
                            }
                            td {
                                style = border
                                textInput {
                                    name = label
                                    this.value = value
                                }
                            }
                        }
                    }
                }
                submitInput { value = "Send" }
            }
        }
    }
}
