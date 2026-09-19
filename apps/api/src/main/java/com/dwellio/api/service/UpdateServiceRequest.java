package com.dwellio.api.service;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateServiceRequest(
        @Size(max = 160) String name,
        @Pattern(regexp = "FIXED|VARIABLE") String billingType,
        @Pattern(regexp = "UPFRONT|MONTHLY_ARREARS") String billingTiming,
        Boolean mandatory,
        @Size(max = 30) String prorationSetting,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
}
