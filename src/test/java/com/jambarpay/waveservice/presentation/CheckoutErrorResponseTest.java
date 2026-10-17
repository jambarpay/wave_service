package com.jambarpay.waveservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jambarpay.waveservice.application.WaveCheckoutLinkService;
import com.jambarpay.waveservice.application.WaveVerificationService;
import com.jambarpay.waveservice.config.KkiapayProperties;
import com.jambarpay.waveservice.config.WaveCheckoutProperties;
import com.jambarpay.waveservice.presentation.controller.WaveController;
import com.jambarpay.waveservice.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutErrorResponseTest {
    @ParameterizedTest
    @CsvSource({
            "'', Checkout signing secret is not configured",
            "too-short, Checkout signing secret must contain at least 32 bytes"
    })
    void invalidSigningSecretReturnsAMessageTheWebClientCanDisplay(String secret, String message) throws Exception {
        WaveCheckoutProperties checkout = new WaveCheckoutProperties();
        checkout.setPublicBaseUrl("https://jambaarpay.com");
        checkout.setSigningSecret(secret);
        KkiapayProperties kkiapay = new KkiapayProperties();
        kkiapay.setPublicKey("test-public-key");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WaveController(
                        new WaveCheckoutLinkService(new ObjectMapper().findAndRegisterModules(), checkout, kkiapay),
                        mock(WaveVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/wave/checkout-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2500,
                                 "callbackUrl":"https://www.jambaarpay.com/enterprise-employees/charge-balances",
                                 "paymentMethod":"momo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_REJECTED"))
                .andExpect(jsonPath("$.message").value(message));
    }
}
