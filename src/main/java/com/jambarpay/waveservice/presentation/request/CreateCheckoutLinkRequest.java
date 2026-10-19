package com.jambarpay.waveservice.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 2048)
    private String callbackUrl;

    @Size(max = 120)
    private String name;
    @Email
    @Size(max = 254)
    private String email;
    @Size(max = 32)
    @Pattern(regexp = "^[+0-9 ()-]*$")
    private String phone;
    @Size(max = 32)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$")
    private String paymentMethod;
    @Size(max = 120)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$")
    private String partnerId;
    @Size(max = 2048)
    private String logoUrl;
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String theme;
}
