package com.dwellio.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    Optional<RoomEntity> findByIdAndPropertyId(UUID id, UUID propertyId);

    boolean existsByPropertyIdAndNameIgnoreCase(UUID propertyId, String name);

    @Query("""
            select r from RoomEntity r
            where r.id = :roomId
            """)
    Optional<RoomEntity> findRoom(@Param("roomId") UUID roomId);
}
