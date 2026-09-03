package com.dwellio.api.checkout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID tenancyId,
        UUID occupancyId,
        LocalDate checkoutDate,
        BigDecimal outstandingReceivable,
        BigDecimal newCheckoutCharges,
        BigDecimal depositBalanceBefore,
        BigDecimal depositDeduction,
        BigDecimal depositRefundDue,
        BigDecimal totalReceivable,
        BigDecimal netReceivable,
        BigDecimal refundDue,
        String currency,
        String status,
        Instant confirmedAt,
        Instant createdAt
) {
}
