package com.dwellio.api.rent;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RentConfigController {

    private final CurrentUserService currentUserService;
    private final RentConfigService rentConfigService;

    public RentConfigController(CurrentUserService currentUserService, RentConfigService rentConfigService) {
        this.currentUserService = currentUserService;
        this.rentConfigService = rentConfigService;
    }

    @GetMapping("/properties/{propertyId}/rent-config")
    public List<RentConfigResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return rentConfigService.list(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/rent-config")
    @ResponseStatus(HttpStatus.CREATED)
    public RentConfigResponse createProperty(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateRentConfigRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return rentConfigService.createPropertyLevel(user, propertyId, request);
    }

    @PostMapping("/rooms/{roomId}/rent-config")
    @ResponseStatus(HttpStatus.CREATED)
    public RentConfigResponse createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateRentConfigRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return rentConfigService.createRoomLevel(user, roomId, request);
    }

    @PostMapping("/beds/{bedId}/rent-config")
    @ResponseStatus(HttpStatus.CREATED)
    public RentConfigResponse createBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bedId,
            @Valid @RequestBody CreateRentConfigRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return rentConfigService.createBedLevel(user, bedId, request);
    }

    @GetMapping("/properties/{propertyId}/rent/resolve")
    public ResolvedRentResponse resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @RequestParam UUID bedId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return rentConfigService.resolve(user, propertyId, bedId, date);
    }
}
