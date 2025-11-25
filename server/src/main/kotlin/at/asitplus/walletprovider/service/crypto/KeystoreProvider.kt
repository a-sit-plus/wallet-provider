package at.asitplus.walletprovider.service.crypto

import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.toCryptoPrivateKey
import at.asitplus.signum.indispensable.toKmpCertificate
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.signerFor
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.SignerBasedKeyMaterial
import at.asitplus.walletprovider.data.ConfigData
import io.ktor.util.*
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey

interface KeyStoreProvider {
    fun getSigner(): KeyMaterial
}

class RealKeyStoreProvider(val config: ConfigData) : KeyStoreProvider {
    override fun getSigner() = loadServerKey()

    private fun loadServerKey() = runCatching {
        val keyStore = File(config.keystore.file).inputStream()
            .use { KeyStore.getInstance("PKCS12").apply { load(it, config.keystore.secret.toCharArray()) } }
        val privateKey =
            (keyStore.getKey(config.keystore.alias, config.keystore.secret.toCharArray()) as PrivateKey).toCryptoPrivateKey().getOrThrow()
        val certificate = (keyStore.getCertificate(config.keystore.alias) as java.security.cert.X509Certificate).toKmpCertificate().getOrThrow()
        val signer: Signer = SignatureAlgorithm.ECDSAwithSHA256.signerFor(privateKey).getOrThrow()
        ProviderKeyMaterial(signer, certificate)
    }.getOrElse { e ->
        throw Throwable("Unable to load KeyMaterial $e")
    }
}

class ProviderKeyMaterial(
    signer: Signer,
    val certificate: X509Certificate
) : SignerBasedKeyMaterial(signer) {
    override suspend fun getCertificate(): X509Certificate = certificate
}