package at.asitplus.walletprovider

import at.asitplus.walletprovider.service.logging.DefaultAntilogAdapter
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class WalletProviderApplication

fun main(args: Array<String>) {
    Napier.base(DefaultAntilogAdapter())
    runApplication<WalletProviderApplication>(*args)
}
