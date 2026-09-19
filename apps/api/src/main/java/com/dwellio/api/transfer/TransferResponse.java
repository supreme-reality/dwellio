package com.dwellio.api.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID tenancyId,
        UUID sourceOccupancyId,
        String sourceOccupancyStatus,
        UUID destinationOccupancyId,
        UUID destinationBedId,
        String destinationOccupancyStatus,
        BigDecimal prorationDiff,
        BigDecimal chargeAmount,
        UUID prorationInvoiceId,
        String currency
) {
}
