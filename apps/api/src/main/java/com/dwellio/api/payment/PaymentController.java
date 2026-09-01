package com.dwellio.api.payment;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final CurrentUserService currentUserService;
    private final PaymentService paymentService;

    public PaymentController(CurrentUserService currentUserService, PaymentService paymentService) {
        this.currentUserService = currentUserService;
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return paymentService.create(user, request);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return paymentService.get(user, paymentId);
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestBody(required = false) ConfirmPaymentRequest ignored
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return paymentService.confirm(user, paymentId);
    }
}
