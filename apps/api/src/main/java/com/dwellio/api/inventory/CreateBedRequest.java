package com.dwellio.api.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBedRequest(
        @NotBlank @Size(max = 100) String name
) {
}
