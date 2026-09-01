package com.dwellio.api.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Pattern(regexp = "FIXED|VARIABLE") String billingType,
        @NotBlank @Pattern(regexp = "UPFRONT|MONTHLY_ARREARS") String billingTiming,
        @NotNull Boolean mandatory,
        @NotBlank @Size(max = 30) String prorationSetting
) {
}
