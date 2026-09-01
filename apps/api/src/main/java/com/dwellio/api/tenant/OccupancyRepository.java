package com.dwellio.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OccupancyRepository extends JpaRepository<OccupancyEntity, UUID> {

    boolean existsByBedIdAndStatus(UUID bedId, String status);

    Optional<OccupancyEntity> findByTenancyIdAndStatus(UUID tenancyId, String status);
}
