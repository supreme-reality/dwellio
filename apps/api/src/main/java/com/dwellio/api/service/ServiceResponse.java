package com.dwellio.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        UUID propertyId,
        String name,
        String billingType,
        String billingTiming,
        boolean mandatory,
        String prorationSetting,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
