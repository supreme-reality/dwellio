package com.dwellio.api.analytics;

import java.math.BigDecimal;

public record RevenueAnalyticsResponse(
        String currency,
        BigDecimal paidAmount,
        BigDecimal finalizedInvoiceAmount
) {
}
