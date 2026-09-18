package com.dwellio.api.property;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class PropertyController {

    private final CurrentUserService currentUserService;
    private final PropertyService propertyService;

    public PropertyController(CurrentUserService currentUserService, PropertyService propertyService) {
        this.currentUserService = currentUserService;
        this.propertyService = propertyService;
    }

    @PostMapping("/organizations/{organizationId}/properties")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreatePropertyRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyService.create(user, organizationId, request);
    }

    @GetMapping("/organizations/{organizationId}/properties")
    public List<PropertyResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyService.listVisible(user, organizationId);
    }

    @DeleteMapping("/organizations/{organizationId}/properties/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        propertyService.softDelete(user, organizationId, propertyId);
    }

    @GetMapping("/properties/{propertyId}")
    public PropertyResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyService.get(user, propertyId);
    }

    @PatchMapping("/properties/{propertyId}")
    public PropertyResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody UpdatePropertyRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return propertyService.update(user, propertyId, request);
    }
}
