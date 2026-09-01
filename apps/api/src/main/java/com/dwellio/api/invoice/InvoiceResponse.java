package com.dwellio.api.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID tenancyId,
        String invoiceType,
        LocalDate billingPeriod,
        LocalDate billingDate,
        LocalDate dueDate,
        String status,
        String currency,
        BigDecimal subtotal,
        BigDecimal total,
        Instant finalizedAt,
        Instant createdAt,
        Instant updatedAt,
        List<InvoiceLineItemResponse> lineItems
) {
    public record InvoiceLineItemResponse(
            UUID id,
            String lineType,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal amount,
            UUID referenceId,
            Instant createdAt
    ) {
    }
}
