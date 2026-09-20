package com.dwellio.api.checkout;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutConfirmRequest(
        @NotNull UUID tenancyId,
        @DecimalMin("0.00") BigDecimal damagesAmount,
        @DecimalMin("0.00") BigDecimal manualChargesAmount,
        Boolean leaveReceivable,
        @Pattern(regexp = "CASH|BANK_TRANSFER|RAZORPAY") String paymentMethod,
        String bankTransferReference,
        String idempotencyKey
) {
}
