package com.dwellio.api.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateServiceChargeRequest(
        @NotNull LocalDate billingPeriod,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal amount,
        String note
) {
}
