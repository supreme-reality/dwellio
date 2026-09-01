package com.dwellio.api.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ServiceConfigRepository extends JpaRepository<ServiceConfigEntity, UUID> {

    List<ServiceConfigEntity> findByServiceIdOrderByEffectiveFromDesc(UUID serviceId);

    @Query("""
            select c from ServiceConfigEntity c
            where c.serviceId = :serviceId
              and c.effectiveFrom < :exclusiveEnd
              and (c.effectiveTo is null or c.effectiveTo > :inclusiveStart)
            """)
    List<ServiceConfigEntity> findOverlapping(
            @Param("serviceId") UUID serviceId,
            @Param("inclusiveStart") LocalDate inclusiveStart,
            @Param("exclusiveEnd") LocalDate exclusiveEnd
    );
}
