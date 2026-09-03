package com.dwellio.api.checkout;

import java.math.BigDecimal;
import java.util.UUID;

public record SettlementRefundResponse(
        UUID settlementId,
        UUID tenancyId,
        UUID refundLedgerId,
        BigDecimal amount,
        String paymentMethod,
        BigDecimal depositBalanceAfter
) {
}
