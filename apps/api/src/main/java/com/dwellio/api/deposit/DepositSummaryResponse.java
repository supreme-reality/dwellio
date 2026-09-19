package com.dwellio.api.deposit;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositSummaryResponse(
        UUID tenancyId,
        BigDecimal balance,
        BigDecimal totalReceipts,
        BigDecimal totalDeductions,
        BigDecimal totalRefunds,
        String currency
) {
}
