package com.dwellio.api.transfer;

import com.dwellio.api.service.ServiceEnrollmentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TransferPreviewResponse(
        UUID tenancyId,
        UUID sourceBedId,
        UUID destinationBedId,
        boolean destinationAvailable,
        BigDecimal sourceRent,
        BigDecimal destinationRent,
        BigDecimal prorationDiff,
        BigDecimal chargeAmount,
        String currency,
        List<ServiceEnrollmentResponse> currentEnrollments
) {
}
