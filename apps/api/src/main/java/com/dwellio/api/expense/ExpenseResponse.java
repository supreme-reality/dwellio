package com.dwellio.api.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID propertyId,
        UUID expenseTypeId,
        BigDecimal amount,
        String currency,
        LocalDate incurredOn,
        String notes,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
