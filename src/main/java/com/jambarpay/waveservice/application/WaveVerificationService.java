package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.infrastructure.PaydunyaClient;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WaveVerificationService {
    private final PaydunyaClient paydunya;

    public WaveVerificationService(PaydunyaClient paydunya) {
        this.paydunya = paydunya;
    }

    public WaveTransactionVerificationResult verifyTransaction(String transactionId, String payerId) {
        PaydunyaClient.InvoiceResponse response = paydunya.confirmInvoice(transactionId);
        if (payerId == null || payerId.isBlank() || response.customData() == null
                || !payerId.equals(response.customData().get("payer_id"))) {
            throw new DomainException("PayDunya invoice does not belong to the authenticated payer");
        }
        String status = response.status() == null ? "" : response.status().toLowerCase(Locale.ROOT);
        String normalized = switch (status) {
            case "completed" -> "SUCCESS";
            case "pending" -> "PENDING";
            case "cancelled", "canceled" -> "CANCELLED";
            case "failed" -> "FAILED";
            default -> throw new DomainException("Unknown PayDunya payment status");
        };
        return new WaveTransactionVerificationResult(response.invoice().token(), response.invoice().totalAmount(),
                normalized, "PAYDUNYA", response.failReason(), response.failReason());
    }
}
