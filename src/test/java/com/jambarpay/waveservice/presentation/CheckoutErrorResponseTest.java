package com.jambarpay.waveservice.presentation;

import com.jambarpay.waveservice.application.PayDunyaClient;
import com.jambarpay.waveservice.application.WaveCheckoutLinkService;
import com.jambarpay.waveservice.application.WaveVerificationService;
import com.jambarpay.waveservice.config.PayDunyaProperties;
import com.jambarpay.waveservice.presentation.controller.WaveController;
import com.jambarpay.waveservice.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutErrorResponseTest {
    @Test
    void missingPayDunyaKeysReturnAnExplicitMessage() throws Exception {
        var properties = new PayDunyaProperties();
        var service = new WaveCheckoutLinkService(new PayDunyaClient(RestClient.builder(), properties), properties);
        var mvc = MockMvcBuilders.standaloneSetup(new WaveController(service, mock(WaveVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/wave/checkout-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":2500,\"callbackUrl\":\"https://jambaarpay.com/return\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_REJECTED"))
                .andExpect(jsonPath("$.message").value("Les clés API PayDunya ne sont pas configurées."));
    }
}
