package com.dwellio.api.property;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssignPropertyManagerRequest(
        @NotBlank @Email String email
) {
}
