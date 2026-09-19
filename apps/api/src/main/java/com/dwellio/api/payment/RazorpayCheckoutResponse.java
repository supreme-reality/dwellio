package com.dwellio.api.payment;

import java.math.BigDecimal;

public record RazorpayCheckoutResponse(
        String keyId,
        String orderId,
        BigDecimal amount,
        String currency
) {
}
