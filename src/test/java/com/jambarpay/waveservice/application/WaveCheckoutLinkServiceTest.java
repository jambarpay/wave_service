package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.config.WaveCheckoutProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveCheckoutLinkServiceTest {

    @Test
    void shouldCreateAndReadCheckoutLink() {
        WaveCheckoutProperties checkoutProperties = new WaveCheckoutProperties();
        checkoutProperties.setSigningSecret("checkout-secret-with-at-least-32-bytes");
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

    @Test
    void shouldAllowTemporaryHttpCheckoutBaseUrl() {
        WaveCheckoutProperties checkoutProperties = new WaveCheckoutProperties();
        checkoutProperties.setPublicBaseUrl("http://149.202.61.30:30088");
        checkoutProperties.setSigningSecret("checkout-secret-with-at-least-32-bytes");

        KkiapayProperties kkiapayProperties = new KkiapayProperties();
        kkiapayProperties.setPublicKey("public-key");

        WaveCheckoutLinkService service = new WaveCheckoutLinkService(
                new ObjectMapper().findAndRegisterModules(),
                checkoutProperties,
                kkiapayProperties,
                Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC)
        );

        WaveCheckoutLinkResult result = service.createCheckoutLink(
                CreateCheckoutLinkRequest.builder()
                        .amount(new BigDecimal("2500"))
                        .callbackUrl("https://jambaarpay.com/enterprise-balance-charge")
                        .build(),
                "http://localhost:8088"
        );

        assertTrue(result.paymentUrl().startsWith("http://149.202.61.30:30088/api/v1/wave/checkout/"));
    }

    @Test
    void shouldRenderSignedCheckoutWithTheOriginalCallbackOriginAndRoute() {
        WaveCheckoutLinkService service = checkoutService(Instant.parse("2026-08-02T10:00:00Z"));
        String token = checkoutToken(service);

        String html = service.renderCheckoutPage(token);

        assertTrue(html.contains("https://www.jambaarpay.com/enterprise-employees/charge-balances"));
        assertTrue(html.contains("https://cdn.kkiapay.me/k.js"));
        assertTrue(html.contains("openKkiapayWidget(checkout)"));
    }

    @Test
    void shouldRejectTamperedPublicCheckoutLinks() {
        WaveCheckoutLinkService service = checkoutService(Instant.parse("2026-08-02T10:00:00Z"));
        String token = checkoutToken(service);

        DomainException error = assertThrows(DomainException.class,
                () -> service.renderCheckoutPage(token + "tampered"));

        assertEquals("Invalid checkout link signature", error.getMessage());
    }

    @Test
    void shouldRejectExpiredPublicCheckoutLinks() {
        String token = checkoutToken(checkoutService(Instant.parse("2026-08-02T10:00:00Z")));
        WaveCheckoutLinkService service = checkoutService(Instant.parse("2026-08-02T10:31:00Z"));

        DomainException error = assertThrows(DomainException.class, () -> service.renderCheckoutPage(token));

        assertEquals("Checkout link has expired", error.getMessage());
    }

    private WaveCheckoutLinkService checkoutService(Instant now) {
        WaveCheckoutProperties checkout = new WaveCheckoutProperties();
        checkout.setPublicBaseUrl("https://jambaarpay.com");
        checkout.setSigningSecret("checkout-secret-with-at-least-32-bytes");
        KkiapayProperties kkiapay = new KkiapayProperties();
        kkiapay.setPublicKey("test-public-key");
        return new WaveCheckoutLinkService(new ObjectMapper().findAndRegisterModules(), checkout, kkiapay,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private String checkoutToken(WaveCheckoutLinkService service) {
        WaveCheckoutLinkResult result = service.createCheckoutLink(CreateCheckoutLinkRequest.builder()
                .amount(new BigDecimal("2500"))
                .callbackUrl("https://www.jambaarpay.com/enterprise-employees/charge-balances")
                .build(), "http://localhost:8088");
        return result.paymentUrl().substring(result.paymentUrl().lastIndexOf('/') + 1);
    }
}
