package com.dwellio.api.checkout;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutConfirmResponse(
        UUID settlementId,
        UUID tenancyId,
        UUID checkoutInvoiceId,
        BigDecimal outstandingReceivables,
        BigDecimal newCheckoutCharges,
        BigDecimal depositBalanceBefore,
        BigDecimal depositDeduction,
        BigDecimal depositRefundDue,
        BigDecimal totalReceivable,
        BigDecimal netReceivable,
        BigDecimal refundDue,
        String currency
) {
}
