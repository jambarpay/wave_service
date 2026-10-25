package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WaveVerificationServiceTest {
    private MockRestServiceServer server;
    private WaveVerificationService service;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = PayDunyaTestSupport.properties();
        service = new WaveVerificationService(new PayDunyaClient(builder, properties), properties);
    }

    @ParameterizedTest
    @CsvSource({"completed,SUCCESS", "pending,PENDING", "cancelled,CANCELLED", "failed,FAILED"})
    void confirmsTheInvoiceOnTheSandboxApiAndMapsOnlyCompletedToSuccess(String status, String expected) throws Exception {
        server.expect(requestTo("https://app.paydunya.com/sandbox-api/v1/checkout-invoice/confirm/test_invoice"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PAYDUNYA-MASTER-KEY", "test-master-key"))
                .andExpect(header("PAYDUNYA-PRIVATE-KEY", "test-private-key"))
                .andExpect(header("PAYDUNYA-TOKEN", "test-api-token"))
                .andRespond(withSuccess(PayDunyaTestSupport.confirmation(status), MediaType.APPLICATION_JSON));
        var result = service.verifyTransaction("test_invoice");
        assertEquals("test_invoice", result.transactionId());
        assertEquals(0, new BigDecimal("3000").compareTo(result.amount()));
        assertEquals("employee_topup_test", result.partnerId());
        assertEquals("PAYDUNYA", result.source());
        assertEquals(expected, result.status());
        assertTrue(result.sandbox());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"test,live", "test_invoice,other_invoice", "3000,not-a-number", "completed,unknown", "hash,missing_hash"})
    void refusesInconsistentOrUnverifiableProviderResponses(String original, String replacement) throws Exception {
        String body = PayDunyaTestSupport.confirmation("completed").replace("\"" + original + "\"", "\"" + replacement + "\"");
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(DomainException.class, () -> service.verifyTransaction("test_invoice"));
        server.verify();
    }

    @Test
    void rejectsAnInvalidSha512Hash() throws Exception {
        String body = PayDunyaTestSupport.confirmation("completed").replaceAll("[a-f0-9]{128}", "0".repeat(128));
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(DomainException.class, () -> service.verifyTransaction("test_invoice"));
        server.verify();
    }

    @Test
    void providerAuthenticationFailureDoesNotExposeItsResponse() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider diagnostic"));
        var error = assertThrows(DownstreamServiceUnavailableException.class, () -> service.verifyTransaction("test_invoice"));
        assertFalse(error.getMessage().contains("private provider diagnostic"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../another", "test_invoice?mode=live", ""})
    void rejectsInvalidTokensBeforeCallingPayDunya(String token) {
        assertThrows(DomainException.class, () -> service.verifyTransaction(token));
        server.verify();
    }
}
