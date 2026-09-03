package com.dwellio.api.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SettlementRefundRequest(
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER") String paymentMethod,
        @Size(max = 200) String bankTransferReference
) {
}
