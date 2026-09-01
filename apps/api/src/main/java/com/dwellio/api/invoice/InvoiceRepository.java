package com.dwellio.api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    List<InvoiceEntity> findByIdIn(Collection<UUID> ids);

    List<InvoiceEntity> findByTenancyIdOrderByBillingDateDescCreatedAtDesc(UUID tenancyId);

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
}
