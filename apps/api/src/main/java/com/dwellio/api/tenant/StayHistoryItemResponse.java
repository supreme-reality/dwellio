package com.dwellio.api.tenant;

import java.time.Instant;
import java.util.UUID;

public record StayHistoryItemResponse(
        UUID tenancyId,
        UUID propertyId,
        String status,
        Instant createdAt
) {
}
