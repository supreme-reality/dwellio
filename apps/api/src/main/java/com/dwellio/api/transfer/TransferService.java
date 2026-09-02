package com.dwellio.api.transfer;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.inventory.BedAvailabilityService;
import com.dwellio.api.inventory.BedEntity;
import com.dwellio.api.inventory.BedRepository;
import com.dwellio.api.inventory.RoomEntity;
import com.dwellio.api.inventory.RoomRepository;
import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceLineItemEntity;
import com.dwellio.api.invoice.InvoiceLineItemRepository;
import com.dwellio.api.invoice.InvoiceRepository;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.rent.RentConfigService;
import com.dwellio.api.rent.ResolvedRentResponse;
import com.dwellio.api.service.ServiceEnrollmentEntity;
import com.dwellio.api.service.ServiceEnrollmentRepository;
import com.dwellio.api.service.ServiceEnrollmentResponse;
import com.dwellio.api.tenant.OccupancyEntity;
import com.dwellio.api.tenant.OccupancyRepository;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final TenancyRepository tenancyRepository;
    private final OccupancyRepository occupancyRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final BedAvailabilityService bedAvailabilityService;
    private final PropertyAccessService propertyAccessService;
    private final RentConfigService rentConfigService;
    private final ServiceEnrollmentRepository enrollmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;

    public TransferService(
            TenancyRepository tenancyRepository,
            OccupancyRepository occupancyRepository,
            BedRepository bedRepository,
            RoomRepository roomRepository,
            BedAvailabilityService bedAvailabilityService,
            PropertyAccessService propertyAccessService,
            RentConfigService rentConfigService,
            ServiceEnrollmentRepository enrollmentRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineItemRepository invoiceLineItemRepository
    ) {
        this.tenancyRepository = tenancyRepository;
        this.occupancyRepository = occupancyRepository;
        this.bedRepository = bedRepository;
        this.roomRepository = roomRepository;
        this.bedAvailabilityService = bedAvailabilityService;
        this.propertyAccessService = propertyAccessService;
        this.rentConfigService = rentConfigService;
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineItemRepository = invoiceLineItemRepository;
    }

    @Transactional(readOnly = true)
    public TransferPreviewResponse preview(AppUserEntity user, TransferPreviewRequest request) {
        TransferContext ctx = loadContext(user, request.tenancyId(), request.destinationBedId());
        Pricing pricing = computePricing(user, ctx, request.prorationOverrideAmount());
        return new TransferPreviewResponse(
                ctx.tenancy().getId(),
                ctx.sourceOccupancy().getBedId(),
                ctx.destinationBed().getId(),
                pricing.destinationAvailable(),
                pricing.sourceRent(),
                pricing.destinationRent(),
                pricing.prorationDiff(),
                pricing.chargeAmount(),
                ctx.property().getDefaultCurrency(),
                listActiveEnrollments(ctx.tenancy().getId())
        );
    }

    @Transactional
    public TransferResponse execute(AppUserEntity user, TransferExecuteRequest request) {
        TransferContext ctx = loadContext(user, request.tenancyId(), request.destinationBedId());
        Pricing pricing = computePricing(user, ctx, request.prorationOverrideAmount());
        if (!pricing.destinationAvailable()) {
            throw bedUnavailable(ctx.destinationBed().getId());
        }

        Instant now = Instant.now();
        OccupancyEntity source = ctx.sourceOccupancy();
        source.setStatus("COMPLETED");
        source.setEndedAt(now);
        source.setUpdatedAt(now);
        occupancyRepository.saveAndFlush(source);

        OccupancyEntity destination;
        try {
            destination = occupancyRepository.saveAndFlush(new OccupancyEntity(
                    UUID.randomUUID(),
                    ctx.tenancy().getId(),
                    ctx.destinationBed().getId(),
                    "ACTIVE",
                    now,
                    null,
                    now,
                    now
            ));
        } catch (DataIntegrityViolationException ex) {
            throw bedUnavailable(ctx.destinationBed().getId());
        }

        UUID invoiceId = null;
        if (pricing.chargeAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoiceId = createProrationInvoice(ctx, pricing.chargeAmount(), now);
        }

        return new TransferResponse(
                ctx.tenancy().getId(),
                source.getId(),
                source.getStatus(),
                destination.getId(),
                destination.getBedId(),
                destination.getStatus(),
                pricing.prorationDiff(),
                pricing.chargeAmount(),
                invoiceId,
                ctx.property().getDefaultCurrency()
        );
    }

    private TransferContext loadContext(AppUserEntity user, UUID tenancyId, UUID destinationBedId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());
        if (!"ACTIVE".equals(tenancy.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Tenancy is not active"
            );
        }

        OccupancyEntity source = occupancyRepository.findByTenancyIdAndStatus(tenancyId, "ACTIVE")
                .orElseThrow(() -> new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "No active occupancy for tenancy"
                ));

        BedEntity destinationBed = bedRepository.findById(destinationBedId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
        RoomEntity destinationRoom = roomRepository.findById(destinationBed.getRoomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Room not found"));
        if (!destinationRoom.getPropertyId().equals(tenancy.getPropertyId())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Destination bed must be on the same property"
            );
        }
        if (destinationBed.getId().equals(source.getBedId())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Destination bed must differ from current bed"
            );
        }

        return new TransferContext(tenancy, property, source, destinationBed);
    }

    private Pricing computePricing(AppUserEntity user, TransferContext ctx, BigDecimal overrideAmount) {
        LocalDate today = LocalDate.now();
        ResolvedRentResponse sourceRent = rentConfigService.resolve(
                user,
                ctx.property().getId(),
                ctx.sourceOccupancy().getBedId(),
                today
        );
        ResolvedRentResponse destRent = rentConfigService.resolve(
                user,
                ctx.property().getId(),
                ctx.destinationBed().getId(),
                today
        );
        BigDecimal prorationDiff = computeProrationDiff(sourceRent.amount(), destRent.amount(), today);
        BigDecimal chargeAmount = overrideAmount != null
                ? overrideAmount.setScale(2, RoundingMode.HALF_UP)
                : prorationDiff.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean available = bedAvailabilityService.isAssignable(ctx.destinationBed());
        return new Pricing(available, sourceRent.amount(), destRent.amount(), prorationDiff, chargeAmount);
    }

    static BigDecimal computeProrationDiff(BigDecimal sourceRent, BigDecimal destinationRent, LocalDate date) {
        int daysInMonth = date.lengthOfMonth();
        int daysRemaining = daysInMonth - date.getDayOfMonth() + 1;
        BigDecimal factor = BigDecimal.valueOf(daysRemaining)
                .divide(BigDecimal.valueOf(daysInMonth), 8, RoundingMode.HALF_UP);
        return destinationRent.subtract(sourceRent)
                .multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private UUID createProrationInvoice(TransferContext ctx, BigDecimal amount, Instant now) {
        LocalDate billingDate = LocalDate.now();
        LocalDate dueDate = billingDate.plusDays(ctx.property().getPaymentDueDays());
        InvoiceEntity invoice = invoiceRepository.save(new InvoiceEntity(
                UUID.randomUUID(),
                ctx.tenancy().getId(),
                "UPFRONT",
                billingDate.withDayOfMonth(1),
                billingDate,
                dueDate,
                "FINALIZED",
                ctx.property().getDefaultCurrency(),
                amount,
                amount,
                now,
                now,
                now
        ));
        invoiceLineItemRepository.save(new InvoiceLineItemEntity(
                UUID.randomUUID(),
                invoice.getId(),
                "RENT",
                "Transfer mid-cycle rent proration",
                BigDecimal.ONE,
                amount,
                amount,
                ctx.destinationBed().getId(),
                now
        ));
        return invoice.getId();
    }

    private List<ServiceEnrollmentResponse> listActiveEnrollments(UUID tenancyId) {
        return enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId).stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .map(this::toEnrollmentResponse)
                .toList();
    }

    private ServiceEnrollmentResponse toEnrollmentResponse(ServiceEnrollmentEntity entity) {
        return new ServiceEnrollmentResponse(
                entity.getId(),
                entity.getTenancyId(),
                entity.getServiceId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiException bedUnavailable(UUID bedId) {
        return new ApiException(
                ErrorCode.BED_UNAVAILABLE,
                HttpStatus.CONFLICT,
                "Destination bed is not available",
                Map.of("bedId", bedId.toString())
        );
    }

    private record TransferContext(
            TenancyEntity tenancy,
            PropertyEntity property,
            OccupancyEntity sourceOccupancy,
            BedEntity destinationBed
    ) {
    }

    private record Pricing(
            boolean destinationAvailable,
            BigDecimal sourceRent,
            BigDecimal destinationRent,
            BigDecimal prorationDiff,
            BigDecimal chargeAmount
    ) {
    }
}
