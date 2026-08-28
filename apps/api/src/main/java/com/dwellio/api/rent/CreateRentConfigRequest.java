package com.dwellio.api.rent;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRentConfigRequest(
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
