package com.dwellio.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoiceEntity, UUID> {

    boolean existsByInvoiceId(UUID invoiceId);

    List<PaymentInvoiceEntity> findByPaymentId(UUID paymentId);

    @Query("""
            select coalesce(sum(pi.amount), 0)
            from PaymentInvoiceEntity pi
            where pi.invoiceId = :invoiceId
            """)
    BigDecimal sumSettledForInvoice(@Param("invoiceId") UUID invoiceId);
}
