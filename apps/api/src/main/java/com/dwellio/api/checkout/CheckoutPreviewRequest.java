package com.dwellio.api.checkout;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutPreviewRequest(
        @NotNull UUID tenancyId,
        @DecimalMin("0.00") BigDecimal damagesAmount,
        @DecimalMin("0.00") BigDecimal manualChargesAmount
) {
}
