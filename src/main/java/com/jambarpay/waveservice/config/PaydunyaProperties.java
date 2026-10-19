package com.jambarpay.waveservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wave.providers.paydunya")
public class PaydunyaProperties {
    private boolean sandbox = true;
    private String baseUrl;
    private String masterKey;
    private String privateKey;
    private String token;
    private String storeName = "Jambaar Pay";
}
