package com.dwellio.api.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RazorpayWebhookController {

    public static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private final RazorpayWebhookService razorpayWebhookService;

    public RazorpayWebhookController(RazorpayWebhookService razorpayWebhookService) {
        this.razorpayWebhookService = razorpayWebhookService;
    }

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<?> handle(
            @RequestBody String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        razorpayWebhookService.verifySignature(rawBody, signature);
        PaymentResponse payment = razorpayWebhookService.process(rawBody);
        if (payment == null) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        return ResponseEntity.status(HttpStatus.OK).body(payment);
    }
}
