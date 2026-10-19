package com.jambarpay.waveservice.presentation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCheckoutLinkResponse {

    private String paymentUrl;
    private Instant expiresAt;
}
