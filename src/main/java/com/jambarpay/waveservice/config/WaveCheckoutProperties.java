package com.jambarpay.waveservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wave.checkout")
public class WaveCheckoutProperties {

    private String publicBaseUrl;
    private String signingSecret;
    private String defaultLogoUrl;
    private String defaultTheme = "#0095ff";
    private long linkTtlMinutes = 30;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String getDefaultLogoUrl() {
        return defaultLogoUrl;
    }

    public void setDefaultLogoUrl(String defaultLogoUrl) {
        this.defaultLogoUrl = defaultLogoUrl;
    }

    public String getDefaultTheme() {
        return defaultTheme;
    }

    public void setDefaultTheme(String defaultTheme) {
        this.defaultTheme = defaultTheme;
    }

    public long getLinkTtlMinutes() {
        return linkTtlMinutes;
    }

    public void setLinkTtlMinutes(long linkTtlMinutes) {
        this.linkTtlMinutes = linkTtlMinutes;
    }
}
