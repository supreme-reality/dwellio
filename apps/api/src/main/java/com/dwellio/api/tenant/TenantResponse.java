package com.dwellio.api.tenant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        UUID organizationId,
        String firstName,
        String lastName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        String governmentId,
        String notes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
