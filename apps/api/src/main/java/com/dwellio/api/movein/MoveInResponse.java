package com.dwellio.api.movein;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MoveInResponse(
        UUID id,
        UUID tenancyId,
        String tenancyStatus,
        UUID tenantId,
        UUID propertyId,
        UUID bedId,
        LocalDate moveInDate,
        String status,
        List<MoveInServiceSelectionResponse> serviceSelections,
        Instant createdAt,
        Instant updatedAt
) {
}
