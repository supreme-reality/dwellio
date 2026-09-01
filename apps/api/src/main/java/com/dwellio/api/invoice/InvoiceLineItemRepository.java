package com.dwellio.api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItemEntity, UUID> {

    List<InvoiceLineItemEntity> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
