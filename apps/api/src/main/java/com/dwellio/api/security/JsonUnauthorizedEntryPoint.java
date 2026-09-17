package com.dwellio.api.security;

import com.dwellio.api.common.ApiErrorResponse;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonUnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JsonUnauthorizedEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JsonUnauthorizedEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String reason = authException.getMessage();
        Throwable cause = authException.getCause();
        String causeMsg = cause != null ? cause.getMessage() : null;
        log.warn(
                "Unauthorized {} {}: {}{}",
                request.getMethod(),
                request.getRequestURI(),
                reason,
                causeMsg != null ? " (cause: " + causeMsg + ")" : ""
        );

        Map<String, Object> details = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason);
        }
        if (causeMsg != null && !causeMsg.isBlank()) {
            details.put("cause", causeMsg);
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                new ApiErrorResponse.ApiErrorBody(
                        ErrorCode.UNAUTHORIZED.name(),
                        "Authentication required",
                        details,
                        RequestIdFilter.currentRequestId(request)
                )
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
