package com.dwellio.api.security;

import com.dwellio.api.common.ApiErrorResponse;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class JsonUnauthorizedEntryPoint implements AuthenticationEntryPoint {

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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                new ApiErrorResponse.ApiErrorBody(
                        ErrorCode.UNAUTHORIZED.name(),
                        "Authentication required",
                        Map.of(),
                        RequestIdFilter.currentRequestId(request)
                )
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
