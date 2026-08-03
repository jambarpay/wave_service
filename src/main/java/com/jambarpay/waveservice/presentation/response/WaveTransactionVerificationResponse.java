package com.jambarpay.waveservice.presentation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaveTransactionVerificationResponse {

    private String transactionId;
    private BigDecimal amount;
    private String status;
    private String source;
    private String reason;
    private String failureMessage;
}
