package com.dwellio.api.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateExpenseRequest(
        UUID expenseTypeId,
        @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
        LocalDate incurredOn,
        @Size(max = 4000) String notes
) {
}
