package com.dwellio.api.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementSnapshotRepository extends JpaRepository<SettlementSnapshotEntity, UUID> {
}
