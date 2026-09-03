package com.dwellio.api.checkout;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CurrentUserService currentUserService;
    private final CheckoutService checkoutService;

    public CheckoutController(CurrentUserService currentUserService, CheckoutService checkoutService) {
        this.currentUserService = currentUserService;
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout/preview")
    public CheckoutPreviewResponse preview(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutPreviewRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return checkoutService.preview(user, request);
    }

    @PostMapping("/checkout")
    public CheckoutConfirmResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutConfirmRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return checkoutService.confirm(user, request);
    }

    @GetMapping("/settlements/{settlementId}")
    public SettlementResponse getSettlement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID settlementId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return checkoutService.getSettlement(user, settlementId);
    }

    @PostMapping("/settlements/{settlementId}/refund")
    public SettlementRefundResponse refund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID settlementId,
            @Valid @RequestBody SettlementRefundRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return checkoutService.refund(user, settlementId, request);
    }
}
