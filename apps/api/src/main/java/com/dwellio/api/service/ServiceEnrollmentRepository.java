package com.dwellio.api.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceEnrollmentRepository extends JpaRepository<ServiceEnrollmentEntity, UUID> {

    List<ServiceEnrollmentEntity> findByTenancyIdOrderByCreatedAtDesc(UUID tenancyId);

    Optional<ServiceEnrollmentEntity> findByTenancyIdAndServiceIdAndStatus(
            UUID tenancyId,
            UUID serviceId,
            String status
    );
}
