package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jambarpay.waveservice.config.PayDunyaProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class PayDunyaClient {
    private final RestClient client;
    private final PayDunyaProperties properties;

    public PayDunyaClient(RestClient.Builder builder, PayDunyaProperties properties) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.apiBaseUrl()).build();
    }

    public JsonNode create(Object invoice) {
        ensureCredentials();
        try {
            return accepted("create", client.post().uri("/checkout-invoice/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("PAYDUNYA-MASTER-KEY", properties.getMasterKey())
                    .header("PAYDUNYA-PRIVATE-KEY", properties.getPrivateKey())
                    .header("PAYDUNYA-TOKEN", properties.getToken())
                    .body(invoice).retrieve().body(JsonNode.class));
        } catch (RestClientException exception) {
            throw unavailable("create", exception);
        }
    }

    public JsonNode confirm(String invoiceToken) {
        ensureCredentials();
        if (invoiceToken == null || !invoiceToken.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new DomainException("La référence de facture PayDunya est invalide.");
        }
        try {
            return accepted("confirm", client.get().uri("/checkout-invoice/confirm/{token}", invoiceToken)
                    .header("PAYDUNYA-MASTER-KEY", properties.getMasterKey())
                    .header("PAYDUNYA-PRIVATE-KEY", properties.getPrivateKey())
                    .header("PAYDUNYA-TOKEN", properties.getToken())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class));
        } catch (RestClientException exception) {
            throw unavailable("confirm", exception);
        }
    }

    private JsonNode accepted(String operation, JsonNode body) {
        if (body == null || !"00".equals(body.path("response_code").asText())) {
            String code = body == null ? "" : body.path("response_code").asText();
            String safeCode = code.matches("[0-9]{1,8}") ? code : "UNCLASSIFIED";
            log.warn("PayDunya response rejected: operation={}, responseCode={}", operation, safeCode);
            throw new DomainException("PAYDUNYA_REJECTED",
                    "PayDunya a refusé la demande. Vérifiez la configuration sandbox et la référence de facture.", safeCode);
        }
        return body;
    }

    private void ensureCredentials() {
        if (blank(properties.getMasterKey()) || blank(properties.getPrivateKey()) || blank(properties.getToken())) {
            throw new DomainException("PAYMENT_KEYS_MISSING", "Les clés API PayDunya ne sont pas configurées.");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private DownstreamServiceUnavailableException unavailable(String operation, RestClientException exception) {
        int status = exception instanceof RestClientResponseException response ? response.getStatusCode().value() : -1;
        // Never log provider response bodies, request headers or exception messages.
        log.warn("PayDunya request failed: operation={}, httpStatus={}, exceptionType={}",
                operation, status, exception.getClass().getSimpleName());
        return new DownstreamServiceUnavailableException("PayDunya est temporairement indisponible.");
    }
}
