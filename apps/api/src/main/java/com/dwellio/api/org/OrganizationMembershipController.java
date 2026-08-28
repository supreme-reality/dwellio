package com.dwellio.api.org;

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
@RequestMapping("/api/v1/organizations/{organizationId}/members")
public class OrganizationMembershipController {

    private final CurrentUserService currentUserService;
    private final OrganizationMembershipService membershipService;

    public OrganizationMembershipController(
            CurrentUserService currentUserService,
            OrganizationMembershipService membershipService
    ) {
        this.currentUserService = currentUserService;
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<OrganizationMemberResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return membershipService.list(user, organizationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationMemberResponse add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return membershipService.add(user, organizationId, request);
    }

    @PatchMapping("/{membershipId}")
    public OrganizationMemberResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return membershipService.updateStatus(user, organizationId, membershipId, request);
    }
}
