package com.dwellio.api.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID organizationId,
        UUID tenancyId,
        @NotBlank @Pattern(regexp = "RAZORPAY|CASH|BANK_TRANSFER") String paymentMethod,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 200) String externalReference,
        @Size(max = 200) String bankTransferReference,
        @Size(max = 200) String idempotencyKey
) {
}
