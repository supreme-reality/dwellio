package com.dwellio.api.movein;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MoveInPaymentRequest(
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER|RAZORPAY") String paymentMethod,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0.00", inclusive = true) BigDecimal depositAmount,
        @Size(max = 200) String bankTransferReference,
        @Size(max = 200) String externalReference,
        @Size(max = 200) String idempotencyKey
) {
}
