package com.dwellio.api.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenancyStayContextResponse(
        UUID id,
        UUID tenantId,
        UUID propertyId,
        String status,
        CurrentOccupancySummary currentOccupancy,
        Instant createdAt,
        Instant updatedAt
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CurrentOccupancySummary(
            UUID occupancyId,
            UUID bedId,
            String status,
            Instant startedAt
    ) {
    }
}
