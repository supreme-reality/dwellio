package com.dwellio.api.expense;

import java.time.Instant;
import java.util.UUID;

public record ExpenseTypeResponse(
        UUID id,
        UUID propertyId,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
