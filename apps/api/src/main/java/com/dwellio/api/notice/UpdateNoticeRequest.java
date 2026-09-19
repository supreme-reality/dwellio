package com.dwellio.api.notice;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateNoticeRequest(
        @Size(max = 200) String title,
        String body,
        Instant expiresAt
) {
}
