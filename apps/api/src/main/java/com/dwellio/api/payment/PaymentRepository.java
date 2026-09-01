package com.dwellio.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByExternalReference(String externalReference);

    List<PaymentEntity> findByTenancyIdAndStatus(UUID tenancyId, String status);
}
