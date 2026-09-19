package com.dwellio.api.deposit;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DepositController {

    private final CurrentUserService currentUserService;
    private final DepositService depositService;

    public DepositController(CurrentUserService currentUserService, DepositService depositService) {
        this.currentUserService = currentUserService;
        this.depositService = depositService;
    }

    @GetMapping("/tenancies/{tenancyId}/deposit")
    public DepositSummaryResponse getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenancyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return depositService.getSummary(user, tenancyId);
    }

    @GetMapping("/tenancies/{tenancyId}/deposit/ledger")
    public DepositLedgerResponse getLedger(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenancyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return depositService.getLedger(user, tenancyId);
    }
}
