package com.dwellio.api.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID propertyId,
        String title,
        String body,
        String status,
        UUID createdByUserId,
        UUID assignedToUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
