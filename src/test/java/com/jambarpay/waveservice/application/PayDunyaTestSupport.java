package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.config.PayDunyaProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class PayDunyaTestSupport {
    static PayDunyaProperties properties() {
        var properties = new PayDunyaProperties();
        properties.setMasterKey("test-master-key");
        properties.setPrivateKey("test-private-key");
        properties.setToken("test-api-token");
        return properties;
    }
    static String confirmation(String status) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                .digest("test-master-key".getBytes(StandardCharsets.UTF_8)));
        return "{\"response_code\":\"00\",\"hash\":\"" + hash + "\",\"mode\":\"test\",\"status\":\"" + status
                + "\",\"invoice\":{\"token\":\"test_invoice\",\"total_amount\":\"3000\"},"
                + "\"custom_data\":{\"payment_reference\":\"employee_topup_test\"}}";
    }
}
