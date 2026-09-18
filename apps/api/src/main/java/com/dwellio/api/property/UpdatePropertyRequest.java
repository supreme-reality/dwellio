package com.dwellio.api.property;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePropertyRequest(
        @Size(max = 160) String name,
        String address,
        @Min(0) @Max(365) Integer paymentDueDays,
        @Pattern(regexp = "[A-Z]{3}") String defaultCurrency
) {
}
