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
public class RoomService {

    private final RoomRepository roomRepository;
    private final PropertyAccessService propertyAccessService;

    public RoomService(RoomRepository roomRepository, PropertyAccessService propertyAccessService) {
        this.roomRepository = roomRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional
    public RoomResponse create(AppUserEntity user, UUID propertyId, CreateRoomRequest request) {
        propertyAccessService.requireInventoryMutator(user, propertyId);

        String name = request.name().trim();
        if (roomRepository.existsByPropertyIdAndNameIgnoreCase(propertyId, name)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Room name already exists");
        }

        Instant now = Instant.now();
        RoomEntity room = roomRepository.save(new RoomEntity(
                UUID.randomUUID(), propertyId, name, "ACTIVE", now, now
        ));
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return roomRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                .map(RoomService::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse update(AppUserEntity user, UUID roomId, UpdateRoomRequest request) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
        propertyAccessService.requireInventoryMutator(user, room.getPropertyId());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (roomRepository.existsByPropertyIdAndNameIgnoreCase(room.getPropertyId(), name)
                    && !room.getName().equalsIgnoreCase(name)) {
                throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Room name already exists");
            }
            room.setName(name);
        }
        if (request.status() != null) {
            room.setStatus(request.status());
        }
        room.setUpdatedAt(Instant.now());
        return toResponse(roomRepository.save(room));
    }

    RoomEntity requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
    }

    private static RoomResponse toResponse(RoomEntity room) {
        return new RoomResponse(room.getId(), room.getPropertyId(), room.getName(), room.getStatus());
    }
}
