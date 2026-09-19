package com.dwellio.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceChargeResponse(
        UUID id,
        UUID serviceEnrollmentId,
        LocalDate billingPeriod,
        BigDecimal amount,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
