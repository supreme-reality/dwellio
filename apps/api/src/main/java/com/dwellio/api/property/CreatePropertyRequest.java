package com.dwellio.api.property;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePropertyRequest(
        @NotBlank @Size(max = 160) String name,
        String address,
        @NotNull @Min(0) @Max(365) Integer paymentDueDays,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String defaultCurrency
) {
}
