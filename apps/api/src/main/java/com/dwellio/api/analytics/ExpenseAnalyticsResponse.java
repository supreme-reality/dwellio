package com.dwellio.api.analytics;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseAnalyticsResponse(
        String currency,
        BigDecimal totalAmount,
        List<ExpenseTypeTotalResponse> byType
) {
}
