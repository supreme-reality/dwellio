package com.dwellio.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BillingRunRepository extends JpaRepository<BillingRunEntity, UUID> {

    Optional<BillingRunEntity> findByBillingPeriod(LocalDate billingPeriod);
}
