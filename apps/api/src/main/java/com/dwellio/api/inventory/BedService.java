package com.dwellio.api.inventory;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BedService {

    private final BedRepository bedRepository;
    private final RoomService roomService;
    private final PropertyAccessService propertyAccessService;
    private final BedAvailabilityService bedAvailabilityService;
    private final OccupancyProbe occupancyProbe;

    public BedService(
            BedRepository bedRepository,
            RoomService roomService,
            PropertyAccessService propertyAccessService,
            BedAvailabilityService bedAvailabilityService,
            OccupancyProbe occupancyProbe
    ) {
        this.bedRepository = bedRepository;
        this.roomService = roomService;
        this.propertyAccessService = propertyAccessService;
        this.bedAvailabilityService = bedAvailabilityService;
        this.occupancyProbe = occupancyProbe;
    }

    @Transactional
    public BedResponse create(AppUserEntity user, UUID roomId, CreateBedRequest request) {
        RoomEntity room = roomService.requireRoom(roomId);
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());

        String name = request.name().trim();
        if (bedRepository.existsByRoomIdAndNameIgnoreCase(roomId, name)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Bed name already exists");
        }

        Instant now = Instant.now();
        BedEntity bed = bedRepository.save(new BedEntity(
                UUID.randomUUID(), roomId, name, "ACTIVE", now, now
        ));
        return toResponse(bed);
    }

    @Transactional(readOnly = true)
    public List<BedResponse> listByRoom(AppUserEntity user, UUID roomId) {
        RoomEntity room = roomService.requireRoom(roomId);
        propertyAccessService.requireReadableProperty(user, room.getPropertyId());
        return bedRepository.findByRoomIdAndStatusOrderByCreatedAtAsc(roomId, "ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BedResponse> listByProperty(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return bedRepository.findActiveByPropertyId(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BedResponse get(AppUserEntity user, UUID bedId) {
        BedEntity bed = requireActiveBed(bedId);
        RoomEntity room = roomService.requireRoom(bed.getRoomId());
        propertyAccessService.requireReadableProperty(user, room.getPropertyId());
        return toResponse(bed);
    }

    @Transactional
    public BedResponse update(AppUserEntity user, UUID bedId, UpdateBedRequest request) {
        BedEntity bed = requireBed(bedId);
        RoomEntity room = roomService.requireRoom(bed.getRoomId());
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (bedRepository.existsByRoomIdAndNameIgnoreCase(bed.getRoomId(), name)
                    && !bed.getName().equalsIgnoreCase(name)) {
                throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Bed name already exists");
            }
            bed.setName(name);
        }
        if (request.status() != null) {
            if ("INACTIVE".equals(request.status()) && occupancyProbe.hasActiveOccupancy(bed.getId())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Transfer or checkout required before deactivating an occupied bed"
                );
            }
            bed.setStatus(request.status());
        }
        bed.setUpdatedAt(Instant.now());
        return toResponse(bedRepository.save(bed));
    }

    @Transactional
    public BedResponse block(AppUserEntity user, UUID bedId, BlockBedRequest request) {
        BedEntity bed = requireActiveBed(bedId);
        RoomEntity room = roomService.requireRoom(bed.getRoomId());
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());

        Instant now = Instant.now();
        bed.setBlockReason(request.reason().trim());
        bed.setBlockedAt(now);
        bed.setBlockedByUserId(user.getId());
        bed.setUpdatedAt(now);
        return toResponse(bedRepository.save(bed));
    }

    @Transactional
    public BedResponse unblock(AppUserEntity user, UUID bedId) {
        BedEntity bed = requireActiveBed(bedId);
        RoomEntity room = roomService.requireRoom(bed.getRoomId());
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());

        bed.setBlockReason(null);
        bed.setBlockedAt(null);
        bed.setBlockedByUserId(null);
        bed.setUpdatedAt(Instant.now());
        return toResponse(bedRepository.save(bed));
    }

    BedEntity requireBed(UUID bedId) {
        return bedRepository.findById(bedId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
    }

    private BedEntity requireActiveBed(UUID bedId) {
        BedEntity bed = requireBed(bedId);
        if (!"ACTIVE".equals(bed.getStatus())) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found");
        }
        return bed;
    }

    private BedResponse toResponse(BedEntity bed) {
        String availability = "ACTIVE".equals(bed.getStatus())
                ? bedAvailabilityService.resolve(bed).name()
                : null;
        return new BedResponse(
                bed.getId(),
                bed.getRoomId(),
                bed.getName(),
                bed.getStatus(),
                availability
        );
    }
}
