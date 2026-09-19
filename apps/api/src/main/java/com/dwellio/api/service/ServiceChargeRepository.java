package com.dwellio.api.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ServiceChargeRepository extends JpaRepository<ServiceChargeEntity, UUID> {

    Optional<ServiceChargeEntity> findByServiceEnrollmentIdAndBillingPeriod(
            UUID serviceEnrollmentId,
            LocalDate billingPeriod
    );
}
