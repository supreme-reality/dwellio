package com.dwellio.api.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String body,
        Instant expiresAt
) {
}
