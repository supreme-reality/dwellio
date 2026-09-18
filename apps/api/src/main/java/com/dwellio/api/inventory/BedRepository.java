package com.dwellio.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BedRepository extends JpaRepository<BedEntity, UUID> {

    List<BedEntity> findByRoomIdAndStatusOrderByCreatedAtAsc(UUID roomId, String status);

    boolean existsByRoomIdAndNameIgnoreCase(UUID roomId, String name);

    @Query("""
            select b from BedEntity b
            join RoomEntity r on r.id = b.roomId
            where r.propertyId = :propertyId
              and b.status = 'ACTIVE'
            order by r.createdAt asc, b.createdAt asc
            """)
    List<BedEntity> findActiveByPropertyId(@Param("propertyId") UUID propertyId);
}
