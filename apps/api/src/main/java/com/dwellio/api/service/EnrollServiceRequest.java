package com.dwellio.api.service;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EnrollServiceRequest(
        @NotNull UUID serviceId,
        @NotNull LocalDate startedAt
) {
}
