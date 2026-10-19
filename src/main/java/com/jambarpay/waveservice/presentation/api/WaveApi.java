package com.jambarpay.waveservice.presentation.api;

import com.jambarpay.waveservice.presentation.request.CreateCheckoutLinkRequest;
import com.jambarpay.waveservice.presentation.response.CreateCheckoutLinkResponse;
import com.jambarpay.waveservice.presentation.response.WaveTransactionVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@Tag(name = "Wave Service", description = "Facade Wave pour les providers de paiement")
@RequestMapping("/api/v1/wave")
public interface WaveApi {

    @Operation(summary = "Créer une facture et un lien de paiement PayDunya")
    @PostMapping("/checkout-links")
    ResponseEntity<CreateCheckoutLinkResponse> createCheckoutLink(
            @Valid @RequestBody CreateCheckoutLinkRequest request,
            @AuthenticationPrincipal Jwt jwt
    );

    @Operation(summary = "Retour PayDunya vers le site ou l'application mobile")
    @GetMapping("/checkout/{state}")
    ResponseEntity<Void> openCheckoutPage(
            @PathVariable String state,
            @RequestParam(name = "token", required = false) String invoiceToken
    );

    @Operation(summary = "Vérifier une facture PayDunya avant de créditer le portefeuille")
    @GetMapping("/transactions/{transactionId}/verify")
    ResponseEntity<WaveTransactionVerificationResponse> verifyTransaction(
            @PathVariable String transactionId,
            @AuthenticationPrincipal Jwt jwt
    );
}
