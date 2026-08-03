package com.jambarpay.waveservice.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCheckoutLinkRequest {

    @NotNull
    @DecimalMin(value = "1.00")
    private BigDecimal amount;

    @NotBlank
    private String callbackUrl;

    private String name;
    private String email;
    private String phone;
    private String paymentMethod;
    private String partnerId;
    private String logoUrl;
    private String theme;
}
