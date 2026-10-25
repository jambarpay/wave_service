package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.config.PayDunyaProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WaveCheckoutLinkService {
    private final PayDunyaClient client;
    private final PayDunyaProperties properties;

    public WaveCheckoutLinkService(PayDunyaClient client, PayDunyaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public WaveCheckoutLinkResult createCheckoutLink(CreateCheckoutLinkRequest request) {
        validateCallback(request.getCallbackUrl());
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().stripTrailingZeros().scale() > 0) {
            throw new DomainException("Le montant doit être un entier positif en FCFA.");
        }
        if (properties.getStoreName() == null || properties.getStoreName().isBlank()) {
            throw new DomainException("Le nom de la boutique PayDunya est requis.");
        }
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("total_amount", request.getAmount());
        invoice.put("description", "Chargement des soldes JambaarPay");
        Map<String, String> customer = new LinkedHashMap<>();
        if (request.getName() != null) customer.put("name", request.getName());
        if (request.getEmail() != null) customer.put("email", request.getEmail());
        if (request.getPhone() != null) customer.put("phone", request.getPhone());
        if (!customer.isEmpty()) invoice.put("customer", customer);
        Map<String, String> customData = request.getPartnerId() == null ? Map.of()
                : Map.of("payment_reference", request.getPartnerId());
        String returnUrl = UriComponentsBuilder.fromUriString(request.getCallbackUrl())
                .replaceQueryParam("token").replaceQueryParam("transactionId")
                .replaceQueryParam("paymentCancelled").build().toUriString();
        String cancelUrl = UriComponentsBuilder.fromUriString(returnUrl)
                .queryParam("paymentCancelled", "true").build().toUriString();
        var response = client.create(Map.of("invoice", invoice,
                "store", Map.of("name", properties.getStoreName()), "custom_data", customData,
                "actions", Map.of("return_url", returnUrl, "cancel_url", cancelUrl)));
        String token = response.path("token").asText();
        String url = response.path("response_text").asText();
        validatePaymentUrl(url, token);
        // Local freshness indication; PayDunya remains authoritative about invoice status.
        return new WaveCheckoutLinkResult(url, Instant.now().plus(24, ChronoUnit.HOURS),
                properties.isSandbox(), "PAYDUNYA", token);
    }

    private void validatePaymentUrl(String url, String token) {
        try {
            URI uri = URI.create(url);
            String path = properties.isSandbox() ? "/sandbox-checkout/invoice/" : "/checkout/invoice/";
            if (!token.matches("[A-Za-z0-9_-]{1,255}") || !"https".equalsIgnoreCase(uri.getScheme())
                    || !("app.paydunya.com".equalsIgnoreCase(uri.getHost()) || "paydunya.com".equalsIgnoreCase(uri.getHost()))
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getQuery() != null || uri.getFragment() != null || !(path + token).equals(uri.getPath())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new DomainException("PayDunya a retourné un lien de paiement incompatible avec le mode configuré.");
        }
    }

    private void validateCallback(String value) {
        try {
            URI uri = URI.create(value);
            boolean local = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !local) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new DomainException("L’URL de retour doit utiliser HTTPS (HTTP autorisé sur localhost).");
        }
    }
}
