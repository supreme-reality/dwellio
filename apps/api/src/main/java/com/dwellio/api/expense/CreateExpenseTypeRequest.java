package com.dwellio.api.expense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExpenseTypeRequest(
        @NotBlank @Size(max = 100) String name
) {
}
