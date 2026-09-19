package com.dwellio.api.expense;

import jakarta.validation.constraints.Size;

public record UpdateExpenseTypeRequest(
        @Size(max = 100) String name,
        Boolean active
) {
}
