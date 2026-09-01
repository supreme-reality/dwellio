package com.dwellio.api.deposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepositLedgerResponse(
        UUID tenancyId,
        BigDecimal balance,
        List<DepositLedgerEntryResponse> entries
) {
    public record DepositLedgerEntryResponse(
            UUID id,
            String type,
            BigDecimal amount,
            String reference,
            String notes,
            Instant createdAt
    ) {
    }
}
