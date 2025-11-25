package at.asitplus.walletprovider.service.crypto

import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert

class MockKeyStoreProvider : KeyStoreProvider {
    override fun getSigner() = EphemeralKeyWithoutCert()
}
