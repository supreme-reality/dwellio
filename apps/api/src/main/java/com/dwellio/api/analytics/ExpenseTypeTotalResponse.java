package com.dwellio.api.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseTypeTotalResponse(
        UUID expenseTypeId,
        String name,
        BigDecimal totalAmount
) {
}
