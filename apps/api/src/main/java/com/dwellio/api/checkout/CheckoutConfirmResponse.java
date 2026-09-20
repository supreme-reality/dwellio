package com.dwellio.api.checkout;

import com.dwellio.api.payment.RazorpayCheckoutResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        String currency,
        RazorpayCheckoutResponse razorpay
) {
}
