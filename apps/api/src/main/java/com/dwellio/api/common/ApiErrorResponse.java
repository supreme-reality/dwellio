package com.dwellio.api.common;

import java.util.Map;

public record ApiErrorResponse(ApiErrorBody error) {

    public record ApiErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            String requestId
    ) {
    }
}
