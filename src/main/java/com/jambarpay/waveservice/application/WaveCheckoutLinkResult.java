package com.jambarpay.waveservice.application;

import java.time.Instant;

public record WaveCheckoutLinkResult(
        String paymentUrl,
        Instant expiresAt
) {
}
