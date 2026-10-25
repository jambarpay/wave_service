package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.config.PayDunyaProperties;
import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class WaveCheckoutLinkServiceTest {
    private PayDunyaProperties properties;
    private MockRestServiceServer server;
    private WaveCheckoutLinkService service;

    @BeforeEach
    void setup() {
        properties = PayDunyaTestSupport.properties();
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WaveCheckoutLinkService(new PayDunyaClient(builder, properties), properties);
    }

    @Test
    void createsASandboxInvoiceWithServerCredentialsAndBoundReturnUrl() {
        server.expect(requestTo("https://app.paydunya.com/sandbox-api/v1/checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PAYDUNYA-MASTER-KEY", "test-master-key"))
                .andExpect(header("PAYDUNYA-PRIVATE-KEY", "test-private-key"))
                .andExpect(header("PAYDUNYA-TOKEN", "test-api-token"))
                .andExpect(jsonPath("$.invoice.total_amount").value(3000))
                .andExpect(jsonPath("$.store.name").value("JambaarPay"))
                .andExpect(jsonPath("$.custom_data.payment_reference").value("employee_topup_test"))
                .andExpect(jsonPath("$.actions.return_url").value("https://jambaarpay.com/return?topUpId=test"))
                .andExpect(jsonPath("$.actions.cancel_url").value("https://jambaarpay.com/return?topUpId=test&paymentCancelled=true"))
                .andRespond(withSuccess(created("https://app.paydunya.com/sandbox-checkout/invoice/test_invoice"), MediaType.APPLICATION_JSON));
        var result = service.createCheckoutLink(request());
        assertEquals("test_invoice", result.checkoutToken());
        assertEquals("PAYDUNYA", result.provider());
        assertTrue(result.sandbox());
        assertTrue(result.expiresAt().isAfter(Instant.now()));
        assertFalse(result.paymentUrl().contains("test-api-token"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/sandbox-checkout/invoice/test_invoice",
            "https://app.paydunya.com/checkout/invoice/test_invoice",
            "https://app.paydunya.com/sandbox-checkout/invoice/another_token",
            "http://app.paydunya.com/sandbox-checkout/invoice/test_invoice",
            "https://app.paydunya.com/sandbox-checkout/invoice/test_invoice?redirect=evil"})
    void rejectsProviderLinksWithTheWrongHostModeOrToken(String url) {
        server.expect(anything()).andRespond(withSuccess(created(url), MediaType.APPLICATION_JSON));
        assertThrows(DomainException.class, () -> service.createCheckoutLink(request()));
        server.verify();
    }

    @Test
    void rejectsBusinessFailureEvenWithHttp200(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"response_code\":\"1001\",\"response_text\":\"sensitive diagnostic\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(DomainException.class, () -> service.createCheckoutLink(request()));
        assertEquals("PAYDUNYA_REJECTED", error.getCode());
        assertEquals("1001", error.getProviderCode());
        assertTrue(output.getAll().contains("operation=create, responseCode=1001"));
        assertFalse(error.getMessage().contains("sensitive diagnostic"));
        assertFalse(output.getAll().contains("sensitive diagnostic"));
        server.verify();
    }

    @Test
    void neverLogsOrReturnsAnUnexpectedProviderCodeValue(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"response_code\":\"private-provider-value\",\"response_text\":\"private-diagnostic\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(DomainException.class, () -> service.createCheckoutLink(request()));
        assertEquals("UNCLASSIFIED", error.getProviderCode());
        assertFalse(output.getAll().contains("private-provider-value"));
        assertFalse(output.getAll().contains("private-diagnostic"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://external.example/return", "javascript:alert(1)", "https://user:pass@example.test/return"})
    void rejectsInvalidCallbacksBeforeCreatingAnInvoice(String callback) {
        var request = request();
        request.setCallbackUrl(callback);
        assertThrows(DomainException.class, () -> service.createCheckoutLink(request));
        server.verify();
    }

    @Test
    void requiresAllThreeKeysBeforeMakingARequest() {
        properties.setToken("");
        assertThrows(DomainException.class, () -> service.createCheckoutLink(request()));
        server.verify();
    }

    private CreateCheckoutLinkRequest request() {
        return CreateCheckoutLinkRequest.builder().amount(new BigDecimal("3000"))
                .partnerId("employee_topup_test")
                .callbackUrl("https://jambaarpay.com/return?topUpId=test&transactionId=stale&token=stale").build();
    }
    private String created(String url) {
        return "{\"response_code\":\"00\",\"response_text\":\"" + url + "\",\"token\":\"test_invoice\"}";
    }
}
