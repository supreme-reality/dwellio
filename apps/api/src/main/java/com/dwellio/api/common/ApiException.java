package com.dwellio.api.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, Collections.emptyMap());
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
