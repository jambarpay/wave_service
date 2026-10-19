package com.jambarpay.waveservice.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jambarpay.waveservice.config.PaydunyaProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Component
public class PaydunyaClient {
    private final RestClient restClient;
    private final PaydunyaProperties properties;

    public PaydunyaClient(RestClient.Builder builder, PaydunyaProperties properties) {
        this.properties = properties;
        String baseUrl = hasText(properties.getBaseUrl()) ? properties.getBaseUrl().trim()
                : "https://app.paydunya.com/" + (properties.isSandbox() ? "sandbox-api" : "api") + "/v1";
        this.restClient = builder.clone().baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON)).build();
    }

    public InvoiceResponse createInvoice(Map<String, Object> payload) {
        ensureConfigured();
        InvoiceResponse response;
        try {
            response = restClient.post().uri("/checkout-invoice/create")
                    .headers(this::authenticate).body(payload).retrieve().body(InvoiceResponse.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
        ensureSuccessfulResponse(response);
        validateInvoiceToken(response.token());
        try {
            URI url = URI.create(response.responseText());
            String expectedPath = (properties.isSandbox() ? "/sandbox-checkout/invoice/" : "/checkout/invoice/")
                    + response.token();
            if (!"https".equalsIgnoreCase(url.getScheme()) || !"app.paydunya.com".equalsIgnoreCase(url.getHost())
                    || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)
                    || !expectedPath.equals(url.getPath()) || url.getFragment() != null) {
                throw unavailable();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw unavailable();
        }
        return response;
    }

    public InvoiceResponse confirmInvoice(String token) {
        validateInvoiceToken(token);
        ensureConfigured();
        InvoiceResponse response;
        try {
            response = restClient.get().uri("/checkout-invoice/confirm/{token}", token)
                    .headers(this::authenticate).retrieve().body(InvoiceResponse.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
        ensureSuccessfulResponse(response);
        if (response.invoice() == null || !token.equals(response.invoice().token()) || !validHash(response.hash())) {
            throw unavailable();
        }
        return response;
    }

    public String storeName() {
        if (!hasText(properties.getStoreName())) {
            throw new DomainException("PayDunya store name is not configured");
        }
        return properties.getStoreName().trim();
    }

    public static void validateInvoiceToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new DomainException("Invalid PayDunya invoice token");
        }
    }

    private void authenticate(org.springframework.http.HttpHeaders headers) {
        headers.set("PAYDUNYA-MASTER-KEY", properties.getMasterKey());
        headers.set("PAYDUNYA-PRIVATE-KEY", properties.getPrivateKey());
        headers.set("PAYDUNYA-TOKEN", properties.getToken());
    }

    private void ensureConfigured() {
        if (!hasText(properties.getMasterKey()) || !hasText(properties.getPrivateKey()) || !hasText(properties.getToken())) {
            throw new DomainException("PayDunya credentials are not configured");
        }
    }

    private void ensureSuccessfulResponse(InvoiceResponse response) {
        if (response == null || !"00".equals(response.responseCode())) {
            throw new DomainException("PayDunya rejected the invoice request. Check the payment configuration.");
        }
    }

    private boolean validHash(String hash) {
        if (hash == null || !hash.matches("[A-Fa-f0-9]{128}")) return false;
        try {
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                    .digest(properties.getMasterKey().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    hash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 is unavailable", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DownstreamServiceUnavailableException unavailable() {
        return new DownstreamServiceUnavailableException("PayDunya is currently unavailable or returned an invalid response");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceResponse(
            @JsonProperty("response_code") String responseCode,
            @JsonProperty("response_text") String responseText,
            String token, String status, String hash, Invoice invoice,
            @JsonProperty("custom_data") Map<String, String> customData,
            @JsonProperty("fail_reason") String failReason
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Invoice(String token, @JsonProperty("total_amount") BigDecimal totalAmount) { }
}
