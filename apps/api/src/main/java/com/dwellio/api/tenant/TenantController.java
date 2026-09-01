package com.dwellio.api.tenant;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TenantController {

    private final CurrentUserService currentUserService;
    private final TenantService tenantService;

    public TenantController(CurrentUserService currentUserService, TenantService tenantService) {
        this.currentUserService = currentUserService;
        this.tenantService = tenantService;
    }

    @PostMapping("/properties/{propertyId}/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return tenantService.create(user, propertyId, request);
    }

    @GetMapping("/properties/{propertyId}/tenants")
    public List<TenantResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return tenantService.listByProperty(user, propertyId);
    }

    @GetMapping("/tenants/{tenantId}")
    public TenantResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenantId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return tenantService.get(user, tenantId);
    }

    @PatchMapping("/tenants/{tenantId}")
    public TenantResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return tenantService.update(user, tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/stay-history")
    public List<StayHistoryItemResponse> stayHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenantId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return tenantService.stayHistory(user, tenantId);
    }
}
