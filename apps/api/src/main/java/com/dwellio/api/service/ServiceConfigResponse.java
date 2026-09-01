package com.dwellio.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceConfigResponse(
        UUID id,
        UUID serviceId,
        BigDecimal amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt,
        Instant updatedAt
) {
}
