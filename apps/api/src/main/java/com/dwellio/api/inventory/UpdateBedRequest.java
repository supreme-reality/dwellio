package com.dwellio.api.inventory;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBedRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
}
