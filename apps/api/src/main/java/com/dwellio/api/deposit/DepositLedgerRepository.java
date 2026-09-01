package com.dwellio.api.deposit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositLedgerRepository extends JpaRepository<DepositLedgerEntity, UUID> {

    List<DepositLedgerEntity> findByTenancyIdOrderByCreatedAtAsc(UUID tenancyId);
}
