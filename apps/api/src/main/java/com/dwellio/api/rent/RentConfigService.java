package com.dwellio.api.rent;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.inventory.BedEntity;
import com.dwellio.api.inventory.BedRepository;
import com.dwellio.api.inventory.RoomEntity;
import com.dwellio.api.inventory.RoomRepository;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RentConfigService {

    private final RentConfigRepository rentConfigRepository;
    private final PropertyAccessService propertyAccessService;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    public RentConfigService(
            RentConfigRepository rentConfigRepository,
            PropertyAccessService propertyAccessService,
            RoomRepository roomRepository,
            BedRepository bedRepository
    ) {
        this.rentConfigRepository = rentConfigRepository;
        this.propertyAccessService = propertyAccessService;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
    }

    @Transactional(readOnly = true)
    public List<RentConfigResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return rentConfigRepository.findByPropertyIdOrderByEffectiveFromAscCreatedAtAsc(propertyId).stream()
                .map(RentConfigService::toResponse)
                .toList();
    }

    @Transactional
    public RentConfigResponse createPropertyLevel(
            AppUserEntity user,
            UUID propertyId,
            CreateRentConfigRequest request
    ) {
        propertyAccessService.requireInventoryMutator(user, propertyId);
        validateRange(request);
        closeOrRejectOverlaps(rentConfigRepository.findPropertyLevel(propertyId), request);
        return save(propertyId, null, null, request);
    }

    @Transactional
    public RentConfigResponse createRoomLevel(AppUserEntity user, UUID roomId, CreateRentConfigRequest request) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());
        validateRange(request);
        closeOrRejectOverlaps(
                rentConfigRepository.findRoomLevel(room.getPropertyId(), roomId),
                request
        );
        return save(room.getPropertyId(), roomId, null, request);
    }

    @Transactional
    public RentConfigResponse createBedLevel(AppUserEntity user, UUID bedId, CreateRentConfigRequest request) {
        BedEntity bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
        RoomEntity room = roomRepository.findById(bed.getRoomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());
        validateRange(request);
        closeOrRejectOverlaps(
                rentConfigRepository.findBedLevel(room.getPropertyId(), bedId),
                request
        );
        return save(room.getPropertyId(), room.getId(), bedId, request);
    }

    @Transactional(readOnly = true)
    public ResolvedRentResponse resolve(AppUserEntity user, UUID propertyId, UUID bedId, LocalDate date) {
        PropertyEntity property = propertyAccessService.requireReadableProperty(user, propertyId);
        BedEntity bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
        RoomEntity room = roomRepository.findById(bed.getRoomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
        if (!room.getPropertyId().equals(propertyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found");
        }

        List<RentConfigEntity> bedConfigs =
                rentConfigRepository.findBedEffective(propertyId, bedId, date);
        if (!bedConfigs.isEmpty()) {
            return toResolved(bedConfigs.getFirst(), "BED", property.getDefaultCurrency());
        }

        List<RentConfigEntity> roomConfigs =
                rentConfigRepository.findRoomEffective(propertyId, room.getId(), date);
        if (!roomConfigs.isEmpty()) {
            return toResolved(roomConfigs.getFirst(), "ROOM", property.getDefaultCurrency());
        }

        List<RentConfigEntity> propertyConfigs = rentConfigRepository.findPropertyEffective(propertyId, date);
        if (propertyConfigs.isEmpty()) {
            throw new ApiException(
                    ErrorCode.NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "No rent configuration for date"
            );
        }
        return toResolved(propertyConfigs.getFirst(), "PROPERTY", property.getDefaultCurrency());
    }

    private RentConfigResponse save(
            UUID propertyId,
            UUID roomId,
            UUID bedId,
            CreateRentConfigRequest request
    ) {
        Instant now = Instant.now();
        RentConfigEntity entity = rentConfigRepository.save(new RentConfigEntity(
                UUID.randomUUID(),
                propertyId,
                roomId,
                bedId,
                request.amount(),
                request.effectiveFrom(),
                request.effectiveTo(),
                now,
                now
        ));
        return toResponse(entity);
    }

    private void closeOrRejectOverlaps(List<RentConfigEntity> existing, CreateRentConfigRequest request) {
        LocalDate newFrom = request.effectiveFrom();
        LocalDate newTo = request.effectiveTo();

        for (RentConfigEntity row : existing) {
            if (!rangesOverlap(row.getEffectiveFrom(), row.getEffectiveTo(), newFrom, newTo)) {
                continue;
            }
            // History-preserving succession: open-ended prior closed at newFrom when new starts after prior.from
            if (row.getEffectiveTo() == null
                    && newTo == null
                    && newFrom.isAfter(row.getEffectiveFrom())) {
                row.setEffectiveTo(newFrom);
                row.setUpdatedAt(Instant.now());
                rentConfigRepository.save(row);
                continue;
            }
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Rent configuration overlaps an existing range at the same level"
            );
        }
    }

    static boolean rangesOverlap(LocalDate from1, LocalDate to1, LocalDate from2, LocalDate to2) {
        LocalDate end1 = to1 == null ? LocalDate.MAX : to1;
        LocalDate end2 = to2 == null ? LocalDate.MAX : to2;
        return from1.isBefore(end2) && from2.isBefore(end1);
    }

    private static void validateRange(CreateRentConfigRequest request) {
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "effectiveTo must be after effectiveFrom"
            );
        }
    }

    private static RentConfigResponse toResponse(RentConfigEntity entity) {
        return new RentConfigResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getRoomId(),
                entity.getBedId(),
                entity.getAmount(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                levelOf(entity)
        );
    }

    private static ResolvedRentResponse toResolved(RentConfigEntity entity, String level, String currency) {
        return new ResolvedRentResponse(
                entity.getAmount(),
                level,
                entity.getId(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                currency
        );
    }

    private static String levelOf(RentConfigEntity entity) {
        if (entity.getBedId() != null) {
            return "BED";
        }
        if (entity.getRoomId() != null) {
            return "ROOM";
        }
        return "PROPERTY";
    }
}
