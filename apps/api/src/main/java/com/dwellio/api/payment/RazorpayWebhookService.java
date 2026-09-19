package com.dwellio.api.payment;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.movein.MoveInService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RazorpayWebhookService {

    private final RazorpayProperties razorpayProperties;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MoveInService moveInService;
    private final ObjectMapper objectMapper;

    public RazorpayWebhookService(
            RazorpayProperties razorpayProperties,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            MoveInService moveInService,
            ObjectMapper objectMapper
    ) {
        this.razorpayProperties = razorpayProperties;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.moveInService = moveInService;
        this.objectMapper = objectMapper;
    }

    public void verifySignature(String rawBody, String signatureHeader) {
        String secret = razorpayProperties.getWebhookSecret();
        if (secret.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Webhook not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Missing Razorpay signature");
        }
        String expected = hmacSha256Hex(secret, rawBody);
        if (!constantTimeEquals(expected, signatureHeader.trim())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid Razorpay signature");
        }
    }

    @Transactional
    public PaymentResponse process(String rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "Malformed webhook payload");
        }

        String event = text(root, "event");
        if (event == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "Missing event type");
        }

        // Capture-only: authorized is acknowledged with no side effects.
        if ("payment.authorized".equals(event)) {
            return null;
        }
        if (!"payment.captured".equals(event)) {
            return null;
        }

        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "Missing payment entity");
        }

        String razorpayPaymentId = text(paymentEntity, "id");
        String razorpayOrderId = text(paymentEntity, "order_id");
        String dwellioPaymentId = text(paymentEntity.path("notes"), "dwellioPaymentId");
        String currency = text(paymentEntity, "currency");
        Long amountPaise = longValue(paymentEntity, "amount");

        Optional<PaymentEntity> match = Optional.empty();
        if (dwellioPaymentId != null) {
            try {
                match = paymentRepository.findById(UUID.fromString(dwellioPaymentId));
            } catch (IllegalArgumentException ignored) {
                // fall through to external reference lookup
            }
        }
        if (match.isEmpty() && razorpayOrderId != null) {
            match = paymentRepository.findByExternalReference(razorpayOrderId);
        }
        if (match.isEmpty() && razorpayPaymentId != null) {
            match = paymentRepository.findByExternalReference(razorpayPaymentId);
        }
        if (match.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found for Razorpay event");
        }

        PaymentEntity payment = match.get();
        if (!"RAZORPAY".equals(payment.getPaymentMethod())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Matched payment is not a Razorpay payment"
            );
        }

        if ("CONFIRMED".equals(payment.getStatus())) {
            // Idempotent replay — no double confirm.
            return paymentService.confirmRazorpayCapture(payment);
        }
        if ("CANCELLED".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
            // Draft cancelled (or otherwise closed) before capture — do not confirm money against cancelled intent.
            return null;
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Payment cannot be confirmed from status " + payment.getStatus()
            );
        }

        assertAmountMatches(payment, amountPaise, currency);

        if (razorpayPaymentId != null) {
            payment.setExternalReference(razorpayPaymentId);
        }

        PaymentResponse confirmed = paymentService.confirmRazorpayCapture(payment);
        moveInService.activateAfterPaymentConfirmed(payment);
        return confirmed;
    }

    private static void assertAmountMatches(PaymentEntity payment, Long amountPaise, String currency) {
        if (amountPaise == null || currency == null || currency.isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Razorpay capture missing amount or currency"
            );
        }
        BigDecimal captured = BigDecimal.valueOf(amountPaise, 2);
        if (payment.getAmount().compareTo(captured) != 0
                || !payment.getCurrency().equalsIgnoreCase(currency.trim())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Razorpay capture amount/currency does not match payment",
                    Map.of(
                            "paymentAmount", payment.getAmount().toPlainString(),
                            "capturedAmount", captured.toPlainString(),
                            "paymentCurrency", payment.getCurrency(),
                            "capturedCurrency", currency.trim().toUpperCase(Locale.ROOT)
                    )
            );
        }
    }

    private static PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrganizationId(),
                payment.getTenancyId(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getExternalReference(),
                payment.getBankTransferReference(),
                payment.getIdempotencyKey(),
                payment.getConfirmedAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                List.of()
        );
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Long longValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    private static String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute Razorpay signature", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
