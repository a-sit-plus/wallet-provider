package at.asitplus.walletprovider.service

import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.logging.DefaultAntilogAdapter
import at.asitplus.walletprovider.service.network.HttpService
import io.github.aakira.napier.Napier

class WalletProviderMain(
    val configData: ConfigData,
    val httpService: HttpService,
) {
    init {
        Napier.base(DefaultAntilogAdapter())
    }
}

