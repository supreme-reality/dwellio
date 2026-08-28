package com.dwellio.api.org;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddOrganizationMemberRequest(
        @NotBlank @Email String email
) {
}
