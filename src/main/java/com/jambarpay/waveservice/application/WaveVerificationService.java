package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.config.PayDunyaProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class WaveVerificationService {
    private final PayDunyaClient client;
    private final PayDunyaProperties properties;

    public WaveVerificationService(PayDunyaClient client, PayDunyaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public WaveTransactionVerificationResult verifyTransaction(String token) {
        var response = client.confirm(token);
        String hash = response.path("hash").asText();
        String mode = response.path("mode").asText();
        var invoice = response.path("invoice");
        if (!validHash(hash) || !(properties.isSandbox() ? "test" : "live").equals(mode)
                || !token.equals(invoice.path("token").asText())) {
            throw new DomainException("La réponse de vérification PayDunya ne correspond pas à cette facture ou à ce mode.");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(invoice.path("total_amount").asText());
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new DomainException("Le montant de la facture PayDunya est invalide.");
        }
        String status = switch (response.path("status").asText().toLowerCase(Locale.ROOT)) {
            case "completed" -> "SUCCESS";
            case "pending" -> "PENDING";
            case "cancelled" -> "CANCELLED";
            case "failed" -> "FAILED";
            default -> throw new DomainException("Le statut de la facture PayDunya est inconnu.");
        };
        var reference = response.path("custom_data").path("payment_reference");
        return new WaveTransactionVerificationResult(token, amount, status, "PAYDUNYA", null, null,
                reference.isTextual() ? reference.asText() : null, properties.isSandbox());
    }

    private boolean validHash(String supplied) {
        try {
            byte[] expected = MessageDigest.getInstance("SHA-512")
                    .digest(properties.getMasterKey().getBytes(StandardCharsets.UTF_8));
            return supplied.matches("[A-Fa-f0-9]{128}")
                    && MessageDigest.isEqual(expected, HexFormat.of().parseHex(supplied));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 unavailable", exception);
        }
    }
}
