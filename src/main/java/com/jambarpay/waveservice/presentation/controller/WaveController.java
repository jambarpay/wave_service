package com.jambarpay.waveservice.presentation.controller;

import com.jambarpay.waveservice.application.WaveCheckoutLinkResult;
import com.jambarpay.waveservice.application.WaveCheckoutLinkService;
import com.jambarpay.waveservice.application.WaveTransactionVerificationResult;
import com.jambarpay.waveservice.application.WaveVerificationService;
import com.jambarpay.waveservice.presentation.api.WaveApi;
import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import com.jambarpay.waveservice.presentation.response.CreateCheckoutLinkResponse;
import com.jambarpay.waveservice.presentation.response.WaveTransactionVerificationResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaveController implements WaveApi {

    private final WaveCheckoutLinkService waveCheckoutLinkService;
    private final WaveVerificationService waveVerificationService;

    public WaveController(
            WaveCheckoutLinkService waveCheckoutLinkService,
            WaveVerificationService waveVerificationService
    ) {
        this.waveCheckoutLinkService = waveCheckoutLinkService;
        this.waveVerificationService = waveVerificationService;
    }

    @Override
    public ResponseEntity<CreateCheckoutLinkResponse> createCheckoutLink(
            CreateCheckoutLinkRequest request
    ) {
        WaveCheckoutLinkResult result = waveCheckoutLinkService.createCheckoutLink(
                request,
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .build()
                        .toUriString()
        );

        return ResponseEntity.ok(
                CreateCheckoutLinkResponse.builder()
                        .paymentUrl(result.paymentUrl())
                        .expiresAt(result.expiresAt())
                        .build()
        );
    }

    @Override
    public ResponseEntity<String> openCheckoutPage(String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(waveCheckoutLinkService.renderCheckoutPage(token));
    }

    @Override
    public ResponseEntity<WaveTransactionVerificationResponse> verifyTransaction(
            String transactionId
    ) {
        WaveTransactionVerificationResult result = waveVerificationService.verifyTransaction(
                transactionId
        );

        return ResponseEntity.ok(
                WaveTransactionVerificationResponse.builder()
                        .transactionId(result.transactionId())
                        .amount(result.amount())
                        .status(result.status())
                        .source(result.source())
                        .reason(result.reason())
                        .failureMessage(result.failureMessage())
                        .build()
        );
    }
}
