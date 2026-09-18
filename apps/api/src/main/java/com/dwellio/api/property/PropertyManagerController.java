package com.dwellio.api.property;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/properties/{propertyId}/managers")
public class PropertyManagerController {

    private final CurrentUserService currentUserService;
    private final PropertyManagerService propertyManagerService;

    public PropertyManagerController(
            CurrentUserService currentUserService,
            PropertyManagerService propertyManagerService
    ) {
        this.currentUserService = currentUserService;
        this.propertyManagerService = propertyManagerService;
    }

    @GetMapping
    public List<PropertyManagerResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyManagerService.list(user, propertyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyManagerResponse assign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody AssignPropertyManagerRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyManagerService.assign(user, propertyId, request);
    }

    @DeleteMapping("/{propertyMembershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @PathVariable UUID propertyMembershipId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        propertyManagerService.remove(user, propertyId, propertyMembershipId);
    }
}
