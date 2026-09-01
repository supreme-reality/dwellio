package com.dwellio.api.movein;

import com.dwellio.api.payment.PaymentResponse;
import com.dwellio.api.payment.RazorpayCheckoutResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoveInPaymentResponse(
        MoveInResponse moveIn,
        PaymentResponse payment,
        RazorpayCheckoutResponse razorpay
) {
}
