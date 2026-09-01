package com.dwellio.api.payment;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceRepository;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<String> MANUAL_METHODS = Set.of("CASH", "BANK_TRANSFER");

    private final PaymentRepository paymentRepository;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyAccessService propertyAccessService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentInvoiceRepository paymentInvoiceRepository,
            InvoiceRepository invoiceRepository,
            TenancyRepository tenancyRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentInvoiceRepository = paymentInvoiceRepository;
        this.invoiceRepository = invoiceRepository;
        this.tenancyRepository = tenancyRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional
    public PaymentResponse create(AppUserEntity user, CreatePaymentRequest request) {
        String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = scaleMoney(request.amount());

        if ("BANK_TRANSFER".equals(method)
                && (request.bankTransferReference() == null || request.bankTransferReference().isBlank())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bank transfer reference is required"
            );
        }

        String idempotencyKey = blankToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
                throw new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "Idempotency key already used",
                        java.util.Map.of("paymentId", existing.getId().toString())
                );
            });
        }

        TenancyEntity tenancy = null;
        if (request.tenancyId() != null) {
            tenancy = requireTenancyInOrg(request.tenancyId(), request.organizationId());
            propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());
        } else {
            requireOrgPaymentMutator(user, request.organizationId());
        }

        Instant now = Instant.now();
        PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                UUID.randomUUID(),
                request.organizationId(),
                tenancy == null ? null : tenancy.getId(),
                method,
                amount,
                currency,
                "PENDING",
                blankToNull(request.externalReference()),
                blankToNull(request.bankTransferReference()),
                idempotencyKey,
                null,
                null,
                now,
                now
        ));

        return toResponse(payment, List.of());
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(AppUserEntity user, UUID paymentId) {
        PaymentEntity payment = requireReadablePayment(user, paymentId);
        return toResponse(payment, paymentInvoiceRepository.findByPaymentId(payment.getId()));
    }

    @Transactional
    public PaymentResponse confirm(AppUserEntity user, UUID paymentId) {
        PaymentEntity payment = requireWritablePayment(user, paymentId);

        if ("CONFIRMED".equals(payment.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Payment is already confirmed and immutable"
            );
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Payment cannot be confirmed from status " + payment.getStatus()
            );
        }
        if (!MANUAL_METHODS.contains(payment.getPaymentMethod())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only CASH and BANK_TRANSFER payments can be confirmed manually"
            );
        }

        List<PaymentInvoiceEntity> settlements = settleUnpaidForPayment(payment, user);

        Instant now = Instant.now();
        payment.setStatus("CONFIRMED");
        payment.setConfirmedAt(now);
        payment.setUpdatedAt(now);

        return toResponse(payment, settlements);
    }

    /**
     * Confirm a captured Razorpay payment and settle invoices (server-owned, no user AuthZ).
     * Amount mismatch leaves payment confirmed without settlement rows (money already taken).
     */
    @Transactional
    public PaymentResponse confirmRazorpayCapture(PaymentEntity payment) {
        if ("CONFIRMED".equals(payment.getStatus())) {
            return toResponse(payment, paymentInvoiceRepository.findByPaymentId(payment.getId()));
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Payment cannot be confirmed from status " + payment.getStatus()
            );
        }
        if (!"RAZORPAY".equals(payment.getPaymentMethod())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Matched payment is not a Razorpay payment"
            );
        }

        Instant now = Instant.now();
        payment.setStatus("CONFIRMED");
        payment.setConfirmedAt(now);
        payment.setUpdatedAt(now);

        List<PaymentInvoiceEntity> settlements = List.of();
        try {
            settlements = settleUnpaidForPayment(payment, null);
        } catch (ApiException ex) {
            if (ex.getCode() != ErrorCode.VALIDATION_FAILED) {
                throw ex;
            }
            // Keep CONFIRMED; invoices remain open for PG to reconcile manually.
        }

        return toResponse(payment, settlements);
    }

    /**
     * Cancel all PENDING payments for a tenancy (e.g. draft move-in cancel).
     */
    @Transactional
    public void cancelPendingForTenancy(UUID tenancyId) {
        Instant now = Instant.now();
        for (PaymentEntity payment : paymentRepository.findByTenancyIdAndStatus(tenancyId, "PENDING")) {
            payment.setStatus("CANCELLED");
            payment.setUpdatedAt(now);
        }
    }

    /**
     * Server-owned settlement: settle all unsettled finalized invoices for the payment tenancy
     * when their total equals the payment amount. No unpaid invoices → empty list.
     * Unpaid exist but sum ≠ amount → reject (no partials).
     * When {@code user} is non-null, each invoice property must be mutable by that user.
     */
    List<PaymentInvoiceEntity> settleUnpaidForPayment(PaymentEntity payment, AppUserEntity user) {
        if (payment.getTenancyId() == null) {
            return List.of();
        }

        List<InvoiceEntity> invoices = invoiceRepository.findUnsettledFinalizedByTenancyAndCurrency(
                payment.getTenancyId(),
                payment.getCurrency()
        );
        if (invoices.isEmpty()) {
            return List.of();
        }

        BigDecimal obligationTotal = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        Instant now = Instant.now();
        List<PaymentInvoiceEntity> rows = new ArrayList<>();

        for (InvoiceEntity invoice : invoices) {
            TenancyEntity invoiceTenancy = tenancyRepository.findById(invoice.getTenancyId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
            if (user != null) {
                propertyAccessService.requireInventoryMutator(user, invoiceTenancy.getPropertyId());
            }

            obligationTotal = obligationTotal.add(invoice.getTotal());
            rows.add(new PaymentInvoiceEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    invoice.getId(),
                    invoice.getTotal(),
                    now
            ));
        }

        if (payment.getAmount().compareTo(obligationTotal) != 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Partial payments are not allowed; payment amount must equal invoice total",
                    java.util.Map.of(
                            "paymentAmount", payment.getAmount().toPlainString(),
                            "invoiceTotal", obligationTotal.toPlainString()
                    )
            );
        }

        return paymentInvoiceRepository.saveAll(rows);
    }

    private PaymentEntity requireReadablePayment(AppUserEntity user, UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));
        assertCanAccessPayment(user, payment);
        return payment;
    }

    private PaymentEntity requireWritablePayment(AppUserEntity user, UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getTenancyId() != null) {
            TenancyEntity tenancy = tenancyRepository.findById(payment.getTenancyId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
            propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());
        } else {
            requireOrgPaymentMutator(user, payment.getOrganizationId());
        }
        return payment;
    }

    private void assertCanAccessPayment(AppUserEntity user, PaymentEntity payment) {
        if (payment.getTenancyId() != null) {
            TenancyEntity tenancy = tenancyRepository.findById(payment.getTenancyId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
            return;
        }
        if (!propertyAccessService.canReadAnyPropertyInOrg(user, payment.getOrganizationId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found");
        }
    }

    private void requireOrgPaymentMutator(AppUserEntity user, UUID organizationId) {
        if (!propertyAccessService.canMutateAnyPropertyInOrg(user, organizationId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Payment access required");
        }
    }

    private TenancyEntity requireTenancyInOrg(UUID tenancyId, UUID organizationId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        var property = propertyAccessService.requireActiveProperty(tenancy.getPropertyId());
        if (!property.getOrganizationId().equals(organizationId)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tenancy does not belong to organization"
            );
        }
        return tenancy;
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Amount must have at most 2 decimal places"
            );
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PaymentResponse toResponse(PaymentEntity payment, List<PaymentInvoiceEntity> settlements) {
        List<PaymentResponse.PaymentSettlementItem> items = settlements.stream()
                .map(s -> new PaymentResponse.PaymentSettlementItem(s.getInvoiceId(), s.getAmount()))
                .toList();
        return new PaymentResponse(
                payment.getId(),
                payment.getOrganizationId(),
                payment.getTenancyId(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getExternalReference(),
                payment.getBankTransferReference(),
                payment.getIdempotencyKey(),
                payment.getConfirmedAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                items
        );
    }
}
