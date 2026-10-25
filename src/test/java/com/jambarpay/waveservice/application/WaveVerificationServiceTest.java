package com.jambarpay.waveservice.application;

import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import org.junit.jupiter.api.Test;
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
    @Test
    void verifiesWithSandboxKeysAndReturnsTheProviderReference() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new WaveVerificationService(builder, properties(true));
        server.expect(requestTo("https://api-sandbox.kkiapay.me/api/v1/transactions/status"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-api-key", "test-public"))
                .andExpect(header("x-private-key", "test-private"))
                .andExpect(header("x-secret-key", "test-secret"))
                .andExpect(content().json("{\"transactionId\":\"transaction-1\"}"))
                .andRespond(withSuccess("""
                        {"transactionId":"transaction-1","status":"SUCCESS","amount":3000,
                         "partnerId":"employee_topup_test","client":{"phone":"test-only"}}
                        """, MediaType.APPLICATION_JSON));
        var result = service.verifyTransaction("transaction-1");
        assertEquals("transaction-1", result.transactionId());
        assertEquals(0, new BigDecimal("3000").compareTo(result.amount()));
        assertEquals("employee_topup_test", result.partnerId());
        assertTrue(result.sandbox());
        server.verify();
    }

    @Test
    void neverInventsAMissingProviderTransactionId() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new WaveVerificationService(builder, properties(false));
        server.expect(requestTo("https://api.kkiapay.me/api/v1/transactions/status"))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"amount\":3000}", MediaType.APPLICATION_JSON));
        assertNull(service.verifyTransaction("transaction-1").transactionId());
        server.verify();
    }

    @Test
    void handlesProviderAuthenticationFailureWithoutExposingItsResponse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new WaveVerificationService(builder, properties(true));
        server.expect(requestTo("https://api-sandbox.kkiapay.me/api/v1/transactions/status"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider diagnostic"));
        var error = assertThrows(DownstreamServiceUnavailableException.class,
                () -> service.verifyTransaction("transaction-1"));
        assertFalse(error.getMessage().contains("private provider diagnostic"));
        server.verify();
    }

    private KkiapayProperties properties(boolean sandbox) {
        var properties = new KkiapayProperties();
        properties.setSandbox(sandbox);
        properties.setPublicKey("test-public");
        properties.setPrivateKey("test-private");
        properties.setSecretKey("test-secret");
        return properties;
    }
}
