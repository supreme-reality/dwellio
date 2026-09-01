package com.dwellio.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST.name(),
                "Malformed request",
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message != null && message.contains("uq_occupancy_active_bed")) {
            return build(
                    HttpStatus.CONFLICT,
                    ErrorCode.BED_UNAVAILABLE.name(),
                    "The selected bed is no longer available.",
                    Collections.emptyMap(),
                    request
            );
        }
        log.error("Data integrity violation requestId={}", RequestIdFilter.currentRequestId(request), ex);
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT.name(),
                "Conflict with existing data",
                Collections.emptyMap(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error(
                "Unhandled exception requestId={}",
                RequestIdFilter.currentRequestId(request),
                ex
        );
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
