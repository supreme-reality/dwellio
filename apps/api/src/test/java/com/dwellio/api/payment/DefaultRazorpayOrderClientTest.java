package com.dwellio.api.payment;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRazorpayOrderClientTest {

    @Test
    void stubsOrderWhenCredentialsBlankAndStubAllowed() {
        RazorpayProperties properties = new RazorpayProperties();
        DefaultRazorpayOrderClient client = new DefaultRazorpayOrderClient(properties, new ObjectMapper());

        RazorpayOrderClient.CreatedOrder order = client.createOrder(
                new BigDecimal("100.00"),
                "INR",
                "movein-test",
                Map.of()
        );

        assertThat(order.orderId()).startsWith("order_test_");
        assertThat(order.keyId()).isEqualTo("rzp_test_local");
    }

    @Test
    void rejectsRazorpayWhenCredentialsBlankAndStubDisabled() {
        RazorpayProperties properties = new RazorpayProperties();
        properties.setAllowStub(false);
        DefaultRazorpayOrderClient client = new DefaultRazorpayOrderClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.createOrder(
                new BigDecimal("100.00"),
                "INR",
                "movein-test",
                Map.of()
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(api.getMessage()).contains("Razorpay is not configured");
                });
    }
}
