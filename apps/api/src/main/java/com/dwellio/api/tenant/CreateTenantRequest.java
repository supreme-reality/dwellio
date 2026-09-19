package com.dwellio.api.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTenantRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 320) String email,
        LocalDate dateOfBirth,
        @Size(max = 40) String gender,
        String address,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 30) String emergencyContactPhone,
        @Size(max = 100) String governmentId,
        String notes
) {
}
