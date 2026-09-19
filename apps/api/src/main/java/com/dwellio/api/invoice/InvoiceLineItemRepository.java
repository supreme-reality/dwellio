package com.dwellio.api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItemEntity, UUID> {

    List<InvoiceLineItemEntity> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    @Query("""
            select (count(li) > 0) from InvoiceLineItemEntity li
            join InvoiceEntity i on i.id = li.invoiceId
            where li.referenceId = :referenceId
              and i.status = 'FINALIZED'
            """)
    boolean existsFinalizedByReferenceId(@Param("referenceId") UUID referenceId);
}
