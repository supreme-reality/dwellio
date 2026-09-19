package com.dwellio.api.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementSnapshotRepository extends JpaRepository<SettlementSnapshotEntity, UUID> {

    Optional<SettlementSnapshotEntity> findByTenancyId(UUID tenancyId);

    boolean existsByTenancyId(UUID tenancyId);
}
