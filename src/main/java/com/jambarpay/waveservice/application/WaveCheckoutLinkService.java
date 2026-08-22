package com.jambarpay.waveservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.config.WaveCheckoutProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.security.MessageDigest;

@Service
public class WaveCheckoutLinkService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String TEMPORARY_PUBLIC_HOST = "149.202.61.30";
    private static final int TEMPORARY_PUBLIC_PORT = 30088;

    private final ObjectMapper objectMapper;
    private final WaveCheckoutProperties checkoutProperties;
    private final KkiapayProperties kkiapayProperties;
    private final Clock clock;

    @Autowired
    public WaveCheckoutLinkService(
            ObjectMapper objectMapper,
            WaveCheckoutProperties checkoutProperties,
            KkiapayProperties kkiapayProperties
    ) {
        this(objectMapper, checkoutProperties, kkiapayProperties, Clock.systemUTC());
    }

    WaveCheckoutLinkService(
            ObjectMapper objectMapper,
            WaveCheckoutProperties checkoutProperties,
            KkiapayProperties kkiapayProperties,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.checkoutProperties = checkoutProperties;
        this.kkiapayProperties = kkiapayProperties;
        this.clock = clock;
    }

    public WaveCheckoutLinkResult createCheckoutLink(
            CreateCheckoutLinkRequest request,
            String fallbackBaseUrl
    ) {
        ensurePublicKeyIsConfigured();
        validateCallbackUrl(request.getCallbackUrl());
        ensureLinkTtlIsValid();

        Instant expiresAt = Instant.now(clock).plusSeconds(
                checkoutProperties.getLinkTtlMinutes() * 60
        );

        String logoUrl = coalesce(request.getLogoUrl(), checkoutProperties.getDefaultLogoUrl());
        validateOptionalPublicUrl(logoUrl, "logoUrl");
        String theme = normalizeTheme(request.getTheme(), checkoutProperties.getDefaultTheme());

        WaveCheckoutLinkPayload payload = new WaveCheckoutLinkPayload(
                request.getAmount(),
                request.getCallbackUrl().trim(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                normalizeBlank(request.getPaymentMethod()),
                normalizeBlank(request.getPartnerId()),
                logoUrl,
                theme,
                expiresAt
        );

        String token = sign(payload);
        String baseUrl = resolvePublicBaseUrl(fallbackBaseUrl);

        return new WaveCheckoutLinkResult(
                baseUrl + "/api/v1/wave/checkout/" + token,
                expiresAt
        );
    }

    public String renderCheckoutPage(String token) {
        ensurePublicKeyIsConfigured();
        WaveCheckoutLinkPayload payload = verifyAndRead(token);

        if (payload.expiresAt() == null || !Instant.now(clock).isBefore(payload.expiresAt())) {
            throw new DomainException("Checkout link has expired");
        }

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>JambarPay Checkout</title>
                  <script src="https://cdn.kkiapay.me/k.js"></script>
                  <style>
                    body {
                      font-family: Arial, sans-serif;
                      background: linear-gradient(135deg, #fff8ef 0%%, #f3efe7 100%%);
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      min-height: 100vh;
                      margin: 0;
                      color: #1f2937;
                    }
                    .card {
                      width: min(92vw, 520px);
                      background: white;
                      border-radius: 20px;
                      box-shadow: 0 18px 60px rgba(15, 23, 42, 0.15);
                      padding: 32px;
                    }
                    h1 {
                      margin-top: 0;
                      margin-bottom: 8px;
                      font-size: 28px;
                    }
                    p {
                      margin-top: 0;
                      line-height: 1.5;
                    }
                    .amount {
                      font-size: 34px;
                      font-weight: 700;
                      margin: 16px 0 20px;
                    }
                    button {
                      width: 100%%;
                      border: 0;
                      border-radius: 14px;
                      padding: 15px 18px;
                      font-size: 16px;
                      font-weight: 700;
                      cursor: pointer;
                      color: white;
                      background: %s;
                    }
                    .hint {
                      margin-top: 14px;
                      font-size: 13px;
                      color: #6b7280;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Paiement JambarPay</h1>
                    <p>Ce lien ouvre le widget Kkiapay pour finaliser le paiement.</p>
                    <div class="amount">%s FCFA</div>
                    <button id="pay-button" type="button">Payer maintenant</button>
                    <div class="hint">Si le widget ne s'ouvre pas automatiquement, clique sur le bouton.</div>
                  </div>
                  <script>
                    const checkout = %s;
                    function openCheckout() {
                      openKkiapayWidget(checkout);
                    }
                    addSuccessListener(response => {
                      if (!checkout.callback) {
                        return;
                      }
                      const redirectUrl = new URL(checkout.callback);
                      if (response && response.transactionId) {
                        redirectUrl.searchParams.set("transactionId", response.transactionId);
                      }
                      redirectUrl.searchParams.set("status", "success");
                      window.location.href = redirectUrl.toString();
                    });
                    document.getElementById("pay-button").addEventListener("click", openCheckout);
                    window.addEventListener("load", openCheckout);
                  </script>
                </body>
                </html>
                """.formatted(
                escapeHtml(payload.theme()),
                escapeHtml(payload.amount().toPlainString()),
                buildCheckoutJavascriptObjectInternal(payload)
        );
    }

    WaveCheckoutLinkPayload verifyAndRead(String token) {
        if (token == null || token.isBlank() || token.length() > 16384 || !token.contains(".")) {
            throw new DomainException("Invalid checkout link");
        }

        String[] parts = token.split("\\.", 2);
        String payloadPart = parts[0];
        String signaturePart = parts[1];

        String expectedSignature = signPayloadPart(payloadPart);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signaturePart.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new DomainException("Invalid checkout link signature");
        }

        try {
            byte[] payloadBytes = BASE64_URL_DECODER.decode(payloadPart);
            WaveCheckoutLinkPayload payload = objectMapper.readValue(payloadBytes, WaveCheckoutLinkPayload.class);
            if (payload == null || payload.amount() == null || payload.callbackUrl() == null
                    || payload.expiresAt() == null) {
                throw new DomainException("Invalid checkout link payload");
            }
            validateCallbackUrl(payload.callbackUrl());
            validateOptionalPublicUrl(payload.logoUrl(), "logoUrl");
            normalizeTheme(payload.theme(), checkoutProperties.getDefaultTheme());
            return payload;
        } catch (Exception exception) {
            throw new DomainException("Invalid checkout link payload");
        }
    }

    private String sign(WaveCheckoutLinkPayload payload) {
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            String payloadPart = BASE64_URL_ENCODER.encodeToString(payloadBytes);
            return payloadPart + "." + signPayloadPart(payloadPart);
        } catch (JsonProcessingException exception) {
            throw new DomainException("Unable to create checkout link");
        }
    }

    private String signPayloadPart(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    resolveSigningSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] signatureBytes = mac.doFinal(
                    payloadPart.getBytes(StandardCharsets.UTF_8)
            );
            return BASE64_URL_ENCODER.encodeToString(signatureBytes);
        } catch (Exception exception) {
            throw new DomainException("Unable to sign checkout link");
        }
    }

    private String resolvePublicBaseUrl(String fallbackBaseUrl) {
        String baseUrl = checkoutProperties.getPublicBaseUrl();
        if (!hasText(baseUrl)) {
            if (!isLocalUrl(fallbackBaseUrl)) {
                throw new DomainException("Public base url must be configured");
            }
            baseUrl = fallbackBaseUrl;
        }
        validateCheckoutBaseUrl(baseUrl);
        return stripTrailingSlash(baseUrl.trim());
    }

    private void validateCheckoutBaseUrl(String value) {
        if (isTemporaryHttpCheckoutUrl(value)) {
            return;
        }
        validatePublicUrl(value, "publicBaseUrl");
    }

    private boolean isTemporaryHttpCheckoutUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return "http".equalsIgnoreCase(uri.getScheme())
                    && TEMPORARY_PUBLIC_HOST.equals(uri.getHost())
                    && uri.getPort() == TEMPORARY_PUBLIC_PORT
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String resolveSigningSecret() {
        String secret;
        if (hasText(checkoutProperties.getSigningSecret())) {
            secret = checkoutProperties.getSigningSecret();
        } else if (hasText(kkiapayProperties.getSecretKey())) {
            secret = kkiapayProperties.getSecretKey();
        } else {
            throw new DomainException("Checkout signing secret is not configured");
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new DomainException("Checkout signing secret must contain at least 32 bytes");
        }
        return secret;
    }

    private void ensurePublicKeyIsConfigured() {
        if (!hasText(kkiapayProperties.getPublicKey())) {
            throw new DomainException("KKIAPAY public key is not configured");
        }
    }

    private String buildCheckoutJavascriptObjectInternal(WaveCheckoutLinkPayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        appendJavascriptProperty(builder, "amount", payload.amount().toPlainString());
        appendJavascriptProperty(builder, "position", "center");
        appendJavascriptProperty(builder, "callback", payload.callbackUrl());
        appendJavascriptProperty(builder, "theme", payload.theme());
        appendJavascriptProperty(builder, "key", kkiapayProperties.getPublicKey());
        appendJavascriptProperty(builder, "url", payload.logoUrl());
        appendJavascriptProperty(builder, "name", payload.name());
        appendJavascriptProperty(builder, "email", payload.email());
        appendJavascriptProperty(builder, "phone", payload.phone());
        appendJavascriptProperty(builder, "paymentmethod", payload.paymentMethod());
        appendJavascriptProperty(builder, "partnerId", payload.partnerId());
        builder.append("sandbox:").append(kkiapayProperties.isSandbox());
        builder.append("}");
        return builder.toString();
    }

    private static void appendJavascriptProperty(
            StringBuilder builder,
            String propertyName,
            String propertyValue
    ) {
        if (!hasText(propertyValue)) {
            return;
        }

        builder.append(propertyName)
                .append(":\"")
                .append(escapeJavascript(propertyValue))
                .append("\",");
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizeBlank(String value) {
        return hasText(value) ? value : null;
    }

    private static String coalesce(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeJavascript(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("<", "\\u003C")
                .replace(">", "\\u003E")
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private void validateCallbackUrl(String callbackUrl) {
        validatePublicUrl(callbackUrl, "callbackUrl");
    }

    private void validateOptionalPublicUrl(String url, String fieldName) {
        if (hasText(url)) {
            validatePublicUrl(url, fieldName);
        }
    }

    private void validatePublicUrl(String value, String fieldName) {
        if (!hasText(value)) {
            throw new DomainException(fieldName + " is required");
        }

        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean localHttp = "http".equalsIgnoreCase(scheme)
                    && ("localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "[::1]".equals(host));
            if ((!"https".equalsIgnoreCase(scheme) && !localHttp)
                    || host == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new DomainException(fieldName + " must be an HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw new DomainException(fieldName + " is invalid");
        }
    }

    private boolean isLocalUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return ("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost()))
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String normalizeTheme(String requestedTheme, String fallbackTheme) {
        String theme = hasText(requestedTheme) ? requestedTheme.trim() : fallbackTheme;
        if (!hasText(theme) || !theme.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new DomainException("Theme must be a hexadecimal color");
        }
        return theme;
    }

    private void ensureLinkTtlIsValid() {
        if (checkoutProperties.getLinkTtlMinutes() < 1
                || checkoutProperties.getLinkTtlMinutes() > 1440) {
            throw new DomainException("Checkout link TTL is invalid");
        }
    }
}
