package com.dwellio.api.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Creates Razorpay Orders for Checkout. Stubbed when credentials are absent and stubbing is allowed.
 */
public interface RazorpayOrderClient {

    record CreatedOrder(String orderId, String keyId, long amountPaise, String currency) {
    }

    CreatedOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes);
}
