package com.jambarpay.waveservice.application;

import java.time.Instant;

public record WaveCheckoutLinkPayload(
        String callbackUrl,
        Instant expiresAt,
        boolean cancelled
) {
}
