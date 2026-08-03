package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Service
public class WaveVerificationService {

    private static final String SANDBOX_BASE_URL = "https://api-sandbox.kkiapay.me";
    private static final String PRODUCTION_BASE_URL = "https://api.kkiapay.me";
    private static final String TRANSACTION_STATUS_PATH = "/api/v1/transactions/status";

    private final RestClient restClient;
    private final KkiapayProperties properties;

    public WaveVerificationService(
            RestClient.Builder restClientBuilder,
            KkiapayProperties properties
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(resolveBaseUrl(properties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public WaveTransactionVerificationResult verifyTransaction(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new DomainException("Transaction id is required");
        }

        ensureCredentialsAreConfigured();

        KkiapayTransactionStatusResponse response;
        try {
            response = restClient.post()
                    .uri(TRANSACTION_STATUS_PATH)
                    .header("x-api-key", properties.getPublicKey())
                    .header("x-secret-key", properties.getSecretKey())
                    .header("x-private-key", properties.getPrivateKey())
                    .body(new KkiapayTransactionStatusRequest(transactionId))
                    .retrieve()
                    .body(KkiapayTransactionStatusResponse.class);
        } catch (RestClientException exception) {
            throw new DownstreamServiceUnavailableException(
                    "KKIAPAY verification is currently unavailable"
            );
        }

        if (response == null) {
            throw new DomainException("KKIAPAY verification returned an empty response");
        }

        return new WaveTransactionVerificationResult(
                response.transactionId() != null ? response.transactionId() : transactionId,
                response.amount(),
                response.status(),
                response.source(),
                response.reason(),
                response.failureMessage()
        );
    }

    private void ensureCredentialsAreConfigured() {
        if (isBlank(properties.getPublicKey())
                || isBlank(properties.getPrivateKey())
                || isBlank(properties.getSecretKey())) {
            throw new DomainException("KKIAPAY credentials are not configured");
        }
    }

    private static String resolveBaseUrl(KkiapayProperties properties) {
        if (!isBlank(properties.getBaseUrl())) {
            return properties.getBaseUrl();
        }

        return properties.isSandbox() ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record KkiapayTransactionStatusRequest(String transactionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KkiapayTransactionStatusResponse(
            String status,
            BigDecimal amount,
            String source,
            String transactionId,
            String reason,
            String failureMessage
    ) {
    }
}
