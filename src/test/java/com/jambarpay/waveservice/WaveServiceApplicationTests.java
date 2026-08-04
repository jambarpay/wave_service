package com.jambarpay.waveservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "wave.checkout.signing-secret=test-signing-secret",
        "wave.providers.kkiapay.public-key=test-public-key",
        "wave.providers.kkiapay.private-key=test-private-key",
        "wave.providers.kkiapay.secret-key=test-secret-key"
})
class WaveServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
