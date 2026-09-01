package com.dwellio.api.service;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EndEnrollmentRequest(
        @NotNull LocalDate endedAt
) {
}
