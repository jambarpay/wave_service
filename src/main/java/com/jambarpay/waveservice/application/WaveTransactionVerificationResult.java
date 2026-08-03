package com.jambarpay.waveservice.application;

import java.math.BigDecimal;

public record WaveTransactionVerificationResult(
        String transactionId,
        BigDecimal amount,
        String status,
        String source,
        String reason,
        String failureMessage
) {
}
