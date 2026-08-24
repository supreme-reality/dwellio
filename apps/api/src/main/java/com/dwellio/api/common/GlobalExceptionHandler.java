package com.dwellio.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode().name(), ex.getMessage(), ex.getDetails(), request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                ErrorCode.NOT_FOUND.name(),
                "Resource not found",
                Collections.emptyMap(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.put(error.getField(), error.getDefaultMessage())
        );
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_FAILED.name(),
                "Validation failed",
                details,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                Collections.emptyMap(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.currentRequestId(request);
        ApiErrorResponse body = new ApiErrorResponse(
                new ApiErrorResponse.ApiErrorBody(code, message, details, requestId)
        );
        return ResponseEntity.status(status).body(body);
    }
}
