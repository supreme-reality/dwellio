package com.dwellio.api.inventory;

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
public class InventoryController {

    private final CurrentUserService currentUserService;
    private final RoomService roomService;
    private final BedService bedService;

    public InventoryController(
            CurrentUserService currentUserService,
            RoomService roomService,
            BedService bedService
    ) {
        this.currentUserService = currentUserService;
        this.roomService = roomService;
        this.bedService = bedService;
    }

    @PostMapping("/properties/{propertyId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return roomService.create(user, propertyId, request);
    }

    @GetMapping("/properties/{propertyId}/rooms")
    public List<RoomResponse> listRooms(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return roomService.list(user, propertyId);
    }

    @PatchMapping("/rooms/{roomId}")
    public RoomResponse updateRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return roomService.update(user, roomId, request);
    }

    @PostMapping("/rooms/{roomId}/beds")
    @ResponseStatus(HttpStatus.CREATED)
    public BedResponse createBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateBedRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.create(user, roomId, request);
    }

    @GetMapping("/rooms/{roomId}/beds")
    public List<BedResponse> listBedsByRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.listByRoom(user, roomId);
    }

    @GetMapping("/properties/{propertyId}/beds")
    public List<BedResponse> listBedsByProperty(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.listByProperty(user, propertyId);
    }

    @GetMapping("/beds/{bedId}")
    public BedResponse getBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bedId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.get(user, bedId);
    }

    @PatchMapping("/beds/{bedId}")
    public BedResponse updateBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bedId,
            @Valid @RequestBody UpdateBedRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.update(user, bedId, request);
    }

    @PostMapping("/beds/{bedId}/block")
    public BedResponse blockBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bedId,
            @Valid @RequestBody BlockBedRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.block(user, bedId, request);
    }

    @PostMapping("/beds/{bedId}/unblock")
    public BedResponse unblockBed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bedId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return bedService.unblock(user, bedId);
    }
}
