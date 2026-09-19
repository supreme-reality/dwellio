package com.dwellio.api.movein;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MoveInController {

    private final CurrentUserService currentUserService;
    private final MoveInService moveInService;

    public MoveInController(CurrentUserService currentUserService, MoveInService moveInService) {
        this.currentUserService = currentUserService;
        this.moveInService = moveInService;
    }

    @PostMapping("/properties/{propertyId}/move-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public MoveInResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateMoveInRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return moveInService.create(user, propertyId, request);
    }

    @GetMapping("/move-ins/{moveInId}")
    public MoveInResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID moveInId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return moveInService.get(user, moveInId);
    }

    @PatchMapping("/move-ins/{moveInId}")
    public MoveInResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID moveInId,
            @Valid @RequestBody UpdateMoveInRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return moveInService.update(user, moveInId, request);
    }

    @PostMapping("/move-ins/{moveInId}/cancel")
    public MoveInResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID moveInId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return moveInService.cancel(user, moveInId);
    }

    @PostMapping("/move-ins/{moveInId}/payment")
    public MoveInPaymentResponse pay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID moveInId,
            @Valid @RequestBody MoveInPaymentRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return moveInService.pay(user, moveInId, request);
    }
}
