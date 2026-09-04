package com.dwellio.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillingRunItemRepository extends JpaRepository<BillingRunItemEntity, UUID> {

    List<BillingRunItemEntity> findByBillingRunIdOrderByCreatedAtAsc(UUID billingRunId);

    List<BillingRunItemEntity> findByStatusOrderByCreatedAtAsc(String status);

    long countByBillingRunId(UUID billingRunId);
}
