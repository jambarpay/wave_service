package com.jambarpay.waveservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "wave.providers.paydunya.master-key=test-master-key",
        "wave.providers.paydunya.private-key=test-private-key",
        "wave.providers.paydunya.token=test-api-token",
        "security.jwt.issuer=jambaarpay-user-service",
        "security.jwt.secret=test-jwt-secret-with-at-least-32-bytes"
})
class WaveServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
