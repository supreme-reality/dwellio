package com.dwellio.api.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID organizationId,
        UUID tenancyId,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        String status,
        String externalReference,
        String bankTransferReference,
        String idempotencyKey,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentSettlementItem> settlements
) {
    public record PaymentSettlementItem(
            UUID invoiceId,
            BigDecimal amount
    ) {
    }
}
