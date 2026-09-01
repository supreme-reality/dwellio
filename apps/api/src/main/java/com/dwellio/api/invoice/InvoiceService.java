package com.dwellio.api.invoice;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.tenant.TenantEntity;
import com.dwellio.api.tenant.TenantRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final TenancyRepository tenancyRepository;
    private final TenantRepository tenantRepository;
    private final PropertyAccessService propertyAccessService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceLineItemRepository invoiceLineItemRepository,
            TenancyRepository tenancyRepository,
            TenantRepository tenantRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineItemRepository = invoiceLineItemRepository;
        this.tenancyRepository = tenancyRepository;
        this.tenantRepository = tenantRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listByProperty(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return invoiceRepository.findByPropertyId(propertyId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listByTenant(AppUserEntity user, UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found"));
        if (!propertyAccessService.canReadAnyPropertyInOrg(user, tenant.getOrganizationId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return invoiceRepository.findByTenantId(tenantId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listByTenancy(AppUserEntity user, UUID tenancyId) {
        requireReadableTenancy(user, tenancyId);
        return invoiceRepository.findByTenancyIdOrderByBillingDateDescCreatedAtDesc(tenancyId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(AppUserEntity user, UUID invoiceId) {
        InvoiceEntity invoice = requireReadableInvoice(user, invoiceId);
        List<InvoiceLineItemEntity> lines = invoiceLineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        return toDetail(invoice, lines);
    }

    /**
     * Guard for future writers: finalized invoices (and their lines) are immutable.
     */
    public void assertMutable(InvoiceEntity invoice) {
        if ("FINALIZED".equals(invoice.getStatus()) || "VOID".equals(invoice.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Finalized invoices are immutable"
            );
        }
    }

    private InvoiceEntity requireReadableInvoice(AppUserEntity user, UUID invoiceId) {
        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice not found"));
        requireReadableTenancy(user, invoice.getTenancyId());
        return invoice;
    }

    private TenancyEntity requireReadableTenancy(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        try {
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found");
        }
        return tenancy;
    }

    private InvoiceResponse toSummary(InvoiceEntity invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getTenancyId(),
                invoice.getInvoiceType(),
                invoice.getBillingPeriod(),
                invoice.getBillingDate(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getCurrency(),
                invoice.getSubtotal(),
                invoice.getTotal(),
                invoice.getFinalizedAt(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                List.of()
        );
    }

    private InvoiceResponse toDetail(InvoiceEntity invoice, List<InvoiceLineItemEntity> lines) {
        List<InvoiceResponse.InvoiceLineItemResponse> items = lines.stream()
                .map(line -> new InvoiceResponse.InvoiceLineItemResponse(
                        line.getId(),
                        line.getLineType(),
                        line.getDescription(),
                        line.getQuantity(),
                        line.getUnitAmount(),
                        line.getAmount(),
                        line.getReferenceId(),
                        line.getCreatedAt()
                ))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getTenancyId(),
                invoice.getInvoiceType(),
                invoice.getBillingPeriod(),
                invoice.getBillingDate(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getCurrency(),
                invoice.getSubtotal(),
                invoice.getTotal(),
                invoice.getFinalizedAt(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                items
        );
    }
}
