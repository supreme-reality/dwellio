package com.dwellio.api.org;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final CurrentUserService currentUserService;
    private final OrganizationService organizationService;

    public OrganizationController(CurrentUserService currentUserService, OrganizationService organizationService) {
        this.currentUserService = currentUserService;
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return organizationService.create(user, request);
    }

    @GetMapping
    public List<OrganizationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return organizationService.listForUser(user);
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return organizationService.getForUser(user, organizationId);
    }
}
