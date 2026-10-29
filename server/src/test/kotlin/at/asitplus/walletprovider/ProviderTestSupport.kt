package at.asitplus.walletprovider

import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.walletprovider.data.ConfigData
import at.asitplus.walletprovider.service.crypto.AttestationService
import at.asitplus.walletprovider.service.crypto.KeyStoreProvider
import at.asitplus.walletprovider.service.crypto.MockAttestationService
import at.asitplus.walletprovider.service.crypto.MockKeyStoreProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ProviderTestSupport {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var configData: ConfigData
}

/** Replaces the key store and the attestation verification, see [at.asitplus.walletprovider.config.ProviderBeans]. */
@Configuration
@Profile("test")
class MockBeans {

    @Bean
    fun keyStoreProvider(): KeyStoreProvider = MockKeyStoreProvider()

    @Bean
    fun attestationService(configData: ConfigData, keyMaterial: KeyMaterial): AttestationService =
        MockAttestationService(configData, keyMaterial)
}
