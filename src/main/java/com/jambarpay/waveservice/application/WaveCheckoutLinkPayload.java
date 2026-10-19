package com.jambarpay.waveservice.application;

import java.math.BigDecimal;
import java.time.Instant;

public record WaveCheckoutLinkPayload(
        BigDecimal amount,
        String callbackUrl,
        String name,
        String email,
        String phone,
        String paymentMethod,
        String partnerId,
        String logoUrl,
        String theme,
        Instant expiresAt
) {
}
