package com.dwellio.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByExternalReference(String externalReference);

    List<PaymentEntity> findByTenancyIdAndStatus(UUID tenancyId, String status);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from PaymentEntity p
            join TenancyEntity t on t.id = p.tenancyId
            where t.propertyId = :propertyId
              and p.status = 'CONFIRMED'
              and p.confirmedAt >= :fromInclusive
              and p.confirmedAt < :toExclusive
            """)
    BigDecimal sumConfirmedAmountForProperty(
            @Param("propertyId") UUID propertyId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
