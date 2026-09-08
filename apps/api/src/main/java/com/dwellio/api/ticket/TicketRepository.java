package com.dwellio.api.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update TicketEntity t
            set t.assignedToUserId = null, t.updatedAt = :now
            where t.assignedToUserId = :userId
              and t.propertyId = :propertyId
              and t.status in ('OPEN', 'IN_PROGRESS')
            """)
    int clearOpenAssigneesOnProperty(
            @Param("userId") UUID userId,
            @Param("propertyId") UUID propertyId,
            @Param("now") Instant now
    );
}
