package com.dwellio.api.billing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-stay invoice work unit. Same shape for local poll adapter and future SQS messages.
 */
public record InvoiceJob(
        UUID billingRunItemId,
        UUID tenancyId,
        LocalDate billingPeriod
) {
}
