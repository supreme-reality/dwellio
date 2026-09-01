package com.dwellio.api.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceEnrollmentResponse(
        UUID id,
        UUID tenancyId,
        UUID serviceId,
        String status,
        LocalDate startedAt,
        LocalDate endedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
