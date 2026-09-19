package com.dwellio.api.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        UUID tenantId,
        UUID tenancyId,
        String name,
        String storageKey,
        String contentType,
        Long sizeBytes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
