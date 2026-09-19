package com.dwellio.api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    List<InvoiceEntity> findByIdIn(Collection<UUID> ids);

    List<InvoiceEntity> findByTenancyIdOrderByBillingDateDescCreatedAtDesc(UUID tenancyId);

    @Query("""
            select (count(i) > 0) from InvoiceEntity i
            where i.tenancyId = :tenancyId
              and i.invoiceType = 'MONTHLY'
              and i.billingPeriod = :billingPeriod
              and i.status <> 'VOID'
            """)
    boolean existsNonVoidMonthly(
            @Param("tenancyId") UUID tenancyId,
            @Param("billingPeriod") LocalDate billingPeriod
    );

    @Query("""
            select (count(i) > 0) from InvoiceEntity i
            where i.tenancyId = :tenancyId
              and i.invoiceType = 'MONTHLY'
              and i.billingPeriod = :billingPeriod
              and i.status = 'FINALIZED'
            """)
    boolean existsFinalizedMonthly(
            @Param("tenancyId") UUID tenancyId,
            @Param("billingPeriod") LocalDate billingPeriod
    );

    @Query("""
            select i from InvoiceEntity i
            where i.tenancyId = :tenancyId
              and i.status = 'FINALIZED'
              and i.currency = :currency
              and not exists (
                  select 1 from PaymentInvoiceEntity pi where pi.invoiceId = i.id
              )
            order by i.billingDate asc, i.createdAt asc
            """)
    List<InvoiceEntity> findUnsettledFinalizedByTenancyAndCurrency(
            @Param("tenancyId") UUID tenancyId,
            @Param("currency") String currency
    );

    @Query("""
            select i from InvoiceEntity i
            join TenancyEntity t on t.id = i.tenancyId
            where t.propertyId = :propertyId
            order by i.billingDate desc, i.createdAt desc
            """)
    List<InvoiceEntity> findByPropertyId(@Param("propertyId") UUID propertyId);

    @Query("""
            select i from InvoiceEntity i
            join TenancyEntity t on t.id = i.tenancyId
            where t.tenantId = :tenantId
            order by i.billingDate desc, i.createdAt desc
            """)
    List<InvoiceEntity> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
            select coalesce(sum(i.total), 0)
            from InvoiceEntity i
            join TenancyEntity t on t.id = i.tenancyId
            where t.propertyId = :propertyId
              and i.status = 'FINALIZED'
              and i.billingDate >= :fromInclusive
              and i.billingDate <= :toInclusive
            """)
    BigDecimal sumFinalizedTotalForProperty(
            @Param("propertyId") UUID propertyId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    @Query("""
            select i from InvoiceEntity i
            join TenancyEntity t on t.id = i.tenancyId
            where t.propertyId = :propertyId
              and i.billingDate >= :fromInclusive
              and i.billingDate <= :toInclusive
            order by i.billingDate asc, i.createdAt asc
            """)
    List<InvoiceEntity> findByPropertyIdAndBillingDateBetween(
            @Param("propertyId") UUID propertyId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );
}
