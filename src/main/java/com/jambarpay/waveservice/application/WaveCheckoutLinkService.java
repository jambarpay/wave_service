package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jambarpay.waveservice.config.WaveCheckoutProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.infrastructure.PaydunyaClient;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WaveCheckoutLinkService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final WaveCheckoutProperties properties;
    private final PaydunyaClient paydunya;
    private final Clock clock;

    @Autowired
    public WaveCheckoutLinkService(ObjectMapper objectMapper, WaveCheckoutProperties properties, PaydunyaClient paydunya) {
        this(objectMapper, properties, paydunya, Clock.systemUTC());
    }

    WaveCheckoutLinkService(ObjectMapper objectMapper, WaveCheckoutProperties properties, PaydunyaClient paydunya, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.paydunya = paydunya;
        this.clock = clock;
    }

    public WaveCheckoutLinkResult createCheckoutLink(CreateCheckoutLinkRequest request, String fallbackBaseUrl, String payerId) {
        validateCallbackUrl(request.getCallbackUrl());
        if (payerId == null || payerId.isBlank()) throw new DomainException("Authenticated payer is required");
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ONE) < 0) {
            throw new DomainException("Payment amount must be at least 1 FCFA");
        }
        if (properties.getLinkTtlMinutes() < 1 || properties.getLinkTtlMinutes() > 1440) {
            throw new DomainException("Checkout link TTL is invalid");
        }
        String logo = hasText(request.getLogoUrl()) ? request.getLogoUrl() : properties.getDefaultLogoUrl();
        if (hasText(logo)) validateWebUrl(logo, "logoUrl");
        Instant expiresAt = Instant.now(clock).plusSeconds(properties.getLinkTtlMinutes() * 60);
        String returnBase = resolvePublicBaseUrl(fallbackBaseUrl) + "/api/v1/wave/checkout/";
        String returnUrl = returnBase + sign(new WaveCheckoutLinkPayload(request.getCallbackUrl().trim(), expiresAt, false));
        String cancelUrl = returnBase + sign(new WaveCheckoutLinkPayload(request.getCallbackUrl().trim(), expiresAt, true));

        Map<String, Object> customer = new LinkedHashMap<>();
        putIfText(customer, "name", request.getName());
        putIfText(customer, "email", request.getEmail());
        putIfText(customer, "phone", request.getPhone());
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("total_amount", request.getAmount());
        invoice.put("description", "Rechargement du portefeuille Jambaar Pay");
        if (!customer.isEmpty()) invoice.put("customer", customer);
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("name", paydunya.storeName());
        putIfText(store, "logo_url", logo);
        Map<String, Object> customData = new LinkedHashMap<>();
        customData.put("payer_id", payerId);
        putIfText(customData, "partner_id", request.getPartnerId());

        PaydunyaClient.InvoiceResponse response = paydunya.createInvoice(Map.of(
                "invoice", invoice, "store", store, "custom_data", customData,
                "actions", Map.of("return_url", returnUrl, "cancel_url", cancelUrl)));
        return new WaveCheckoutLinkResult(response.responseText(), expiresAt, response.token());
    }

    public URI checkoutReturnUrl(String state, String invoiceToken) {
        WaveCheckoutLinkPayload payload = verifyAndRead(state);
        if (!payload.cancelled() || hasText(invoiceToken)) PaydunyaClient.validateInvoiceToken(invoiceToken);
        // A browser redirect never credits a wallet. The authenticated top-up verifies this token with PayDunya.
        return UriComponentsBuilder.fromUriString(payload.callbackUrl())
                .replaceQueryParam("transactionId")
                .replaceQueryParam("token", hasText(invoiceToken) ? invoiceToken : null)
                .replaceQueryParam("status", payload.cancelled() ? "cancelled" : "success")
                .replaceQueryParam("provider", "paydunya").build(true).toUri();
    }

    WaveCheckoutLinkPayload verifyAndRead(String token) {
        if (token == null || token.isBlank() || token.length() > 16384) throw new DomainException("Invalid checkout link");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(signPayload(parts[0]).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw new DomainException("Invalid checkout link signature");
        }
        WaveCheckoutLinkPayload payload;
        try {
            payload = objectMapper.readValue(DECODER.decode(parts[0]), WaveCheckoutLinkPayload.class);
        } catch (Exception exception) {
            throw new DomainException("Invalid checkout link payload");
        }
        if (payload == null || payload.expiresAt() == null) throw new DomainException("Invalid checkout link payload");
        validateCallbackUrl(payload.callbackUrl());
        if (!Instant.now(clock).isBefore(payload.expiresAt())) throw new DomainException("Checkout link has expired");
        return payload;
    }

    private String sign(WaveCheckoutLinkPayload payload) {
        try {
            String encoded = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            return encoded + "." + signPayload(encoded);
        } catch (JsonProcessingException exception) {
            throw new DomainException("Unable to create checkout link");
        }
    }

    private String signPayload(String payload) {
        String secret = properties.getSigningSecret();
        if (!hasText(secret)) throw new DomainException("Checkout signing secret is not configured");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new DomainException("Checkout signing secret must contain at least 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new DomainException("Unable to sign checkout link");
        }
    }

    private String resolvePublicBaseUrl(String fallback) {
        String base = properties.getPublicBaseUrl();
        if (!hasText(base)) {
            URI uri = parseUri(fallback, "publicBaseUrl");
            if (!isLocal(uri)) throw new DomainException("Public base url must be configured");
            base = fallback;
        }
        // Retain the existing temporary VPS endpoint while HTTPS is configured.
        if (!"http://149.202.61.30:30088".equals(base)) validateWebUrl(base, "publicBaseUrl");
        URI uri = parseUri(base, "publicBaseUrl");
        if (uri.getRawQuery() != null) throw new DomainException("publicBaseUrl must not contain a query");
        return base.trim().replaceAll("/+$", "");
    }

    private void validateCallbackUrl(String value) {
        URI uri = parseUri(value, "callbackUrl");
        if ("jambaarpay".equals(uri.getScheme()) && "payment".equals(uri.getHost())
                && "/callback".equals(uri.getPath()) && uri.getUserInfo() == null
                && uri.getPort() == -1 && uri.getFragment() == null && uri.getRawQuery() == null) return;
        validateWebUrl(value, "callbackUrl");
    }

    private void validateWebUrl(String value, String field) {
        URI uri = parseUri(value, field);
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !("http".equalsIgnoreCase(uri.getScheme()) && isLocal(uri)))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new DomainException(field + " must be an HTTPS URL");
        }
    }

    private URI parseUri(String value, String field) {
        if (!hasText(value)) throw new DomainException(field + " is required");
        try {
            return new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new DomainException(field + " is invalid");
        }
    }

    private boolean isLocal(URI uri) {
        return "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
    }

    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (hasText(value)) map.put(key, value.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
