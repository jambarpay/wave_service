package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.config.WaveCheckoutProperties;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveCheckoutLinkServiceTest {

    @Test
    void shouldCreateAndReadCheckoutLink() {
        WaveCheckoutProperties checkoutProperties = new WaveCheckoutProperties();
        checkoutProperties.setSigningSecret("checkout-secret");
        checkoutProperties.setLinkTtlMinutes(30);
        checkoutProperties.setDefaultTheme("#123456");

        KkiapayProperties kkiapayProperties = new KkiapayProperties();
        kkiapayProperties.setPublicKey("public-key");
        kkiapayProperties.setSecretKey("secret-key");
        kkiapayProperties.setSandbox(true);

        WaveCheckoutLinkService service = new WaveCheckoutLinkService(
                new ObjectMapper().findAndRegisterModules(),
                checkoutProperties,
                kkiapayProperties,
                Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC)
        );

        WaveCheckoutLinkResult result = service.createCheckoutLink(
                CreateCheckoutLinkRequest.builder()
                        .amount(new BigDecimal("2500"))
                        .callbackUrl("https://example.com/payments/complete")
                        .name("Diali")
                        .paymentMethod("momo")
                        .build(),
                "http://localhost:8088"
        );

        assertTrue(result.paymentUrl().startsWith("http://localhost:8088/api/v1/wave/checkout/"));

        String token = result.paymentUrl().substring(
                result.paymentUrl().lastIndexOf('/') + 1
        );
        WaveCheckoutLinkPayload payload = service.verifyAndRead(token);

        assertEquals(new BigDecimal("2500"), payload.amount());
        assertEquals("https://example.com/payments/complete", payload.callbackUrl());
        assertEquals("Diali", payload.name());
        assertEquals("momo", payload.paymentMethod());
    }
}
