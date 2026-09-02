package com.dwellio.api.transfer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferPreviewRequest(
        @NotNull UUID tenancyId,
        @NotNull UUID destinationBedId,
        @DecimalMin("0.00") BigDecimal prorationOverrideAmount
) {
}
