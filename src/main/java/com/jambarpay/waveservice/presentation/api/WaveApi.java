package com.jambarpay.waveservice.presentation.api;

import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import com.jambarpay.waveservice.presentation.response.CreateCheckoutLinkResponse;
import com.jambarpay.waveservice.presentation.response.WaveTransactionVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Wave Service", description = "Facade Wave pour les providers de paiement")
@RequestMapping("/api/v1/wave")
public interface WaveApi {

    @Operation(summary = "Créer un lien de paiement Kkiapay hébergé par Wave")
    @PostMapping("/checkout-links")
    ResponseEntity<CreateCheckoutLinkResponse> createCheckoutLink(
            @Valid @RequestBody CreateCheckoutLinkRequest request
    );

    @Operation(summary = "Ouvrir une page de paiement Kkiapay")
    @GetMapping(value = "/checkout/{token}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> openCheckoutPage(
            @PathVariable String token
    );

    @Operation(summary = "Vérifier une transaction Wave")
    @GetMapping("/transactions/{transactionId}/verify")
    ResponseEntity<WaveTransactionVerificationResponse> verifyTransaction(
            @PathVariable String transactionId
    );
}
