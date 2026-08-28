package com.dwellio.api.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockBedRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
