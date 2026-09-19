package com.dwellio.api.checkout;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutPreviewResponse(
        UUID tenancyId,
        UUID occupancyId,
        BigDecimal outstandingReceivables,
        BigDecimal newCheckoutCharges,
        BigDecimal rentProration,
        BigDecimal damagesAmount,
        BigDecimal manualChargesAmount,
        BigDecimal variableChargesAmount,
        BigDecimal fixedMonthlyArrearsAmount,
        BigDecimal depositBalanceBefore,
        BigDecimal depositDeduction,
        BigDecimal depositRefundDue,
        BigDecimal totalReceivable,
        BigDecimal netReceivable,
        BigDecimal refundDue,
        String currency
) {
}
