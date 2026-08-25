package com.dwellio.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "11111111-1111-1111-1111-111111111111");
    }

    @Test
    void apiExceptionReturnsStandardEnvelope() {
        ApiException ex = new ApiException(
                ErrorCode.CONFLICT,
                HttpStatus.CONFLICT,
                "Bed unavailable",
                Map.of("bedId", "bed-1")
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().error().message()).isEqualTo("Bed unavailable");
        assertThat(response.getBody().error().details()).containsEntry("bedId", "bed-1");
        assertThat(response.getBody().error().requestId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void notFoundReturnsRequestId() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                new org.springframework.web.servlet.NoHandlerFoundException("GET", "/missing", null),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().error().requestId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(UUID.fromString(response.getBody().error().requestId())).isNotNull();
    }

    @Test
    void unexpectedErrorReturnsInternalEnvelope() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().error().requestId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void malformedBodyReturnsBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad json"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().error().message()).isEqualTo("Malformed request");
        assertThat(response.getBody().error().requestId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }
}
