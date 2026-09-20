package com.dwellio.api.payment;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DefaultRazorpayOrderClient implements RazorpayOrderClient {

    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DefaultRazorpayOrderClient(RazorpayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreatedOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        long amountPaise = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        String keyId = properties.getKeyId();
        String keySecret = properties.getKeySecret();

        if (keyId.isBlank() || keySecret.isBlank()) {
            if (!properties.isAllowStub()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Razorpay is not configured"
                );
            }
            // Local/test: deterministic fake order without calling Razorpay.
            return new CreatedOrder("order_test_" + UUID.randomUUID().toString().replace("-", ""), keyId.isBlank() ? "rzp_test_local" : keyId, amountPaise, currency);
        }

        try {
            String notesJson = notes.entrySet().stream()
                    .map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(e.getValue()) + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
            String body = """
                    {"amount":%d,"currency":"%s","receipt":"%s","notes":%s}
                    """.formatted(amountPaise, escape(currency), escape(receipt), notesJson);

            String basic = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders"))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unable to create Razorpay order",
                        Map.of("status", response.statusCode())
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            String orderId = root.path("id").asText(null);
            if (orderId == null || orderId.isBlank()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Razorpay order response missing id");
            }
            return new CreatedOrder(orderId, keyId, amountPaise, currency);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Razorpay order creation failed"
            );
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
