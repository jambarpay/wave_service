package com.jambarpay.waveservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wave.providers.paydunya")
@Getter
@Setter
public class PayDunyaProperties {
    private boolean sandbox = true;
    private String masterKey;
    private String privateKey;
    private String token;
    private String storeName = "JambaarPay";

    public String apiBaseUrl() {
        return sandbox ? "https://app.paydunya.com/sandbox-api/v1" : "https://app.paydunya.com/api/v1";
    }
}
