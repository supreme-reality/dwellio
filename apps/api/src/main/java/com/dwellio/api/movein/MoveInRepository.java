package com.dwellio.api.movein;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MoveInRepository extends JpaRepository<MoveInEntity, UUID> {

    java.util.Optional<MoveInEntity> findByTenancyIdAndStatus(UUID tenancyId, String status);
}
