package com.dwellio.api.movein;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.deposit.DepositLedgerEntity;
import com.dwellio.api.deposit.DepositLedgerRepository;
import com.dwellio.api.inventory.BedAvailabilityService;
import com.dwellio.api.inventory.BedEntity;
import com.dwellio.api.inventory.BedRepository;
import com.dwellio.api.inventory.RoomEntity;
import com.dwellio.api.inventory.RoomRepository;
import com.dwellio.api.payment.PaymentEntity;
import com.dwellio.api.payment.PaymentRepository;
import com.dwellio.api.payment.PaymentResponse;
import com.dwellio.api.payment.RazorpayCheckoutResponse;
import com.dwellio.api.payment.RazorpayOrderClient;
import com.dwellio.api.payment.RazorpayProperties;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.service.ServiceCatalogRepository;
import com.dwellio.api.service.ServiceEnrollmentService;
import com.dwellio.api.service.ServiceEntity;
import com.dwellio.api.tenant.OccupancyEntity;
import com.dwellio.api.tenant.OccupancyRepository;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.tenant.TenantEntity;
import com.dwellio.api.tenant.TenantRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MoveInService {

    private static final Set<String> IMMEDIATE_METHODS = Set.of("CASH", "BANK_TRANSFER");

    private final MoveInRepository moveInRepository;
    private final TenancyRepository tenancyRepository;
    private final TenantRepository tenantRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final OccupancyRepository occupancyRepository;
    private final PaymentRepository paymentRepository;
    private final DepositLedgerRepository depositLedgerRepository;
    private final MoveInServiceSelectionRepository moveInServiceSelectionRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final ServiceEnrollmentService serviceEnrollmentService;
    private final BedAvailabilityService bedAvailabilityService;
    private final PropertyAccessService propertyAccessService;
    private final RazorpayOrderClient razorpayOrderClient;
    private final RazorpayProperties razorpayProperties;

    public MoveInService(
            MoveInRepository moveInRepository,
            TenancyRepository tenancyRepository,
            TenantRepository tenantRepository,
            BedRepository bedRepository,
            RoomRepository roomRepository,
            OccupancyRepository occupancyRepository,
            PaymentRepository paymentRepository,
            DepositLedgerRepository depositLedgerRepository,
            MoveInServiceSelectionRepository moveInServiceSelectionRepository,
            ServiceCatalogRepository serviceCatalogRepository,
            ServiceEnrollmentService serviceEnrollmentService,
            BedAvailabilityService bedAvailabilityService,
            PropertyAccessService propertyAccessService,
            RazorpayOrderClient razorpayOrderClient,
            RazorpayProperties razorpayProperties
    ) {
        this.moveInRepository = moveInRepository;
        this.tenancyRepository = tenancyRepository;
        this.tenantRepository = tenantRepository;
        this.bedRepository = bedRepository;
        this.roomRepository = roomRepository;
        this.occupancyRepository = occupancyRepository;
        this.paymentRepository = paymentRepository;
        this.depositLedgerRepository = depositLedgerRepository;
        this.moveInServiceSelectionRepository = moveInServiceSelectionRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.serviceEnrollmentService = serviceEnrollmentService;
        this.bedAvailabilityService = bedAvailabilityService;
        this.propertyAccessService = propertyAccessService;
        this.razorpayOrderClient = razorpayOrderClient;
        this.razorpayProperties = razorpayProperties;
    }

    @Transactional
    public MoveInResponse create(AppUserEntity user, UUID propertyId, CreateMoveInRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        TenantEntity tenant = requireTenantInOrg(request.tenantId(), property.getOrganizationId());
        requireActiveBedOnProperty(request.bedId(), property.getId());

        Instant now = Instant.now();
        TenancyEntity tenancy = tenancyRepository.save(new TenancyEntity(
                UUID.randomUUID(),
                tenant.getId(),
                property.getId(),
                "ACTIVE",
                now,
                now
        ));

        MoveInEntity moveIn = moveInRepository.save(new MoveInEntity(
                UUID.randomUUID(),
                tenancy.getId(),
                request.bedId(),
                "DRAFT",
                request.moveInDate(),
                now,
                now
        ));

        replaceServiceSelections(moveIn.getId(), property.getId(), request.serviceSelections(), now);
        return toResponse(moveIn, tenancy, tenant.getId());
    }

    @Transactional(readOnly = true)
    public MoveInResponse get(AppUserEntity user, UUID moveInId) {
        MoveInEntity moveIn = requireMoveIn(moveInId);
        TenancyEntity tenancy = requireTenancy(moveIn.getTenancyId());
        requireReadableScope(user, tenancy.getPropertyId());
        return toResponse(moveIn, tenancy, tenancy.getTenantId());
    }

    @Transactional
    public MoveInResponse update(AppUserEntity user, UUID moveInId, UpdateMoveInRequest request) {
        MoveInEntity moveIn = requireMoveIn(moveInId);
        TenancyEntity tenancy = requireTenancy(moveIn.getTenancyId());
        requireMutatorScope(user, tenancy.getPropertyId());
        assertDraft(moveIn);

        if (request.bedId() != null) {
            requireActiveBedOnProperty(request.bedId(), tenancy.getPropertyId());
            moveIn.setBedId(request.bedId());
        }
        if (request.moveInDate() != null) {
            moveIn.setMoveInDate(request.moveInDate());
        }
        Instant now = Instant.now();
        if (request.serviceSelections() != null) {
            replaceServiceSelections(moveIn.getId(), tenancy.getPropertyId(), request.serviceSelections(), now);
        }
        moveIn.setUpdatedAt(now);
        return toResponse(moveIn, tenancy, tenancy.getTenantId());
    }

    @Transactional
    public MoveInResponse cancel(AppUserEntity user, UUID moveInId) {
        MoveInEntity moveIn = requireMoveIn(moveInId);
        TenancyEntity tenancy = requireTenancy(moveIn.getTenancyId());
        requireMutatorScope(user, tenancy.getPropertyId());
        assertDraft(moveIn);

        Instant now = Instant.now();
        for (PaymentEntity pending : paymentRepository.findByTenancyIdAndStatus(tenancy.getId(), "PENDING")) {
            pending.setStatus("CANCELLED");
            pending.setUpdatedAt(now);
        }

        moveIn.setStatus("CANCELLED");
        moveIn.setUpdatedAt(now);
        tenancy.setStatus("CANCELLED");
        tenancy.setUpdatedAt(now);

        return toResponse(moveIn, tenancy, tenancy.getTenantId());
    }

    /**
     * Workflow façade over generic Payment. CASH/BANK_TRANSFER confirm + activate atomically.
     * RAZORPAY creates PENDING payment + Checkout order; activation runs on capture webhook.
     */
    @Transactional
    public MoveInPaymentResponse pay(AppUserEntity user, UUID moveInId, MoveInPaymentRequest request) {
        MoveInEntity moveIn = requireMoveIn(moveInId);
        TenancyEntity tenancy = requireTenancy(moveIn.getTenancyId());
        requireMutatorScope(user, tenancy.getPropertyId());
        assertDraft(moveIn);

        String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!IMMEDIATE_METHODS.contains(method) && !"RAZORPAY".equals(method)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported payment method"
            );
        }
        if ("BANK_TRANSFER".equals(method)
                && (request.bankTransferReference() == null || request.bankTransferReference().isBlank())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bank transfer reference is required"
            );
        }

        BigDecimal amount = scaleMoney(request.amount());
        BigDecimal depositAmount = request.depositAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : scaleMoney(request.depositAmount());
        if (depositAmount.compareTo(amount) > 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Deposit amount cannot exceed payment amount"
            );
        }

        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        PropertyEntity property = propertyAccessService.requireActiveProperty(tenancy.getPropertyId());

        String idempotencyKey = blankToNull(request.idempotencyKey());

        BedEntity bed = bedRepository.findById(moveIn.getBedId())
                .orElseThrow(() -> bedUnavailable(moveIn.getBedId()));
        if (!bedAvailabilityService.isAssignable(bed)) {
            throw bedUnavailable(bed.getId());
        }

        Instant now = Instant.now();

        if ("RAZORPAY".equals(method)) {
            if (idempotencyKey != null) {
                Optional<PaymentEntity> byKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
                if (byKey.isPresent()) {
                    PaymentEntity existing = byKey.get();
                    if ("PENDING".equals(existing.getStatus())
                            && "RAZORPAY".equals(existing.getPaymentMethod())
                            && tenancy.getId().equals(existing.getTenancyId())) {
                        return razorpayPendingResponse(moveIn, tenancy, existing);
                    }
                    throw new ApiException(
                            ErrorCode.CONFLICT,
                            HttpStatus.CONFLICT,
                            "Idempotency key already used",
                            Map.of("paymentId", existing.getId().toString())
                    );
                }
            }

            List<PaymentEntity> openPending = paymentRepository.findByTenancyIdAndStatus(tenancy.getId(), "PENDING");
            if (!openPending.isEmpty()) {
                PaymentEntity existing = openPending.get(0);
                throw new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "An open PENDING payment already exists for this move-in",
                        Map.of("paymentId", existing.getId().toString())
                );
            }

            UUID paymentId = UUID.randomUUID();
            RazorpayOrderClient.CreatedOrder order = razorpayOrderClient.createOrder(
                    amount,
                    currency,
                    "movein-" + moveIn.getId(),
                    Map.of("dwellioPaymentId", paymentId.toString())
            );

            PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                    paymentId,
                    property.getOrganizationId(),
                    tenancy.getId(),
                    method,
                    amount,
                    currency,
                    "PENDING",
                    order.orderId(),
                    null,
                    idempotencyKey,
                    depositAmount.compareTo(BigDecimal.ZERO) > 0 ? depositAmount : null,
                    null,
                    now,
                    now
            ));

            return new MoveInPaymentResponse(
                    toResponse(moveIn, tenancy, tenancy.getTenantId()),
                    toPaymentResponse(payment),
                    new RazorpayCheckoutResponse(order.keyId(), order.orderId(), amount, currency)
            );
        }

        if (idempotencyKey != null) {
            paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
                throw new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "Idempotency key already used",
                        Map.of("paymentId", existing.getId().toString())
                );
            });
        }

        PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                UUID.randomUUID(),
                property.getOrganizationId(),
                tenancy.getId(),
                method,
                amount,
                currency,
                "CONFIRMED",
                blankToNull(request.externalReference()),
                blankToNull(request.bankTransferReference()),
                idempotencyKey,
                depositAmount.compareTo(BigDecimal.ZERO) > 0 ? depositAmount : null,
                now,
                now,
                now
        ));

        activateMoveIn(moveIn, tenancy, payment, now);

        return new MoveInPaymentResponse(
                toResponse(moveIn, tenancy, tenancy.getTenantId()),
                toPaymentResponse(payment),
                null
        );
    }

    private MoveInPaymentResponse razorpayPendingResponse(
            MoveInEntity moveIn,
            TenancyEntity tenancy,
            PaymentEntity payment
    ) {
        String keyId = razorpayProperties.getKeyId().isBlank()
                ? "rzp_test_local"
                : razorpayProperties.getKeyId();
        return new MoveInPaymentResponse(
                toResponse(moveIn, tenancy, tenancy.getTenantId()),
                toPaymentResponse(payment),
                new RazorpayCheckoutResponse(
                        keyId,
                        payment.getExternalReference(),
                        payment.getAmount(),
                        payment.getCurrency()
                )
        );
    }

    /**
     * Called after Razorpay capture confirms a PENDING payment. No-op if no DRAFT move-in for tenancy.
     * Bed conflicts are swallowed so payment confirmation / settlement are not rolled back (PG handles manually).
     */
    @Transactional
    public void activateAfterPaymentConfirmed(PaymentEntity payment) {
        if (payment.getTenancyId() == null) {
            return;
        }
        if (!"CONFIRMED".equals(payment.getStatus())) {
            return;
        }
        MoveInEntity moveIn = moveInRepository.findByTenancyIdAndStatus(payment.getTenancyId(), "DRAFT")
                .orElse(null);
        if (moveIn == null) {
            return;
        }
        TenancyEntity tenancy = requireTenancy(moveIn.getTenancyId());
        try {
            activateMoveIn(moveIn, tenancy, payment, Instant.now());
        } catch (ApiException ex) {
            if (ex.getCode() == ErrorCode.BED_UNAVAILABLE) {
                return;
            }
            throw ex;
        }
    }

    private void activateMoveIn(MoveInEntity moveIn, TenancyEntity tenancy, PaymentEntity payment, Instant now) {
        BigDecimal depositAmount = payment.getIntendedDepositAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : payment.getIntendedDepositAmount();

        if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            depositLedgerRepository.save(new DepositLedgerEntity(
                    UUID.randomUUID(),
                    tenancy.getId(),
                    "RECEIPT",
                    depositAmount,
                    payment.getId().toString(),
                    "Move-in deposit collection",
                    now
            ));
        }

        BedEntity bed = bedRepository.findById(moveIn.getBedId())
                .orElseThrow(() -> bedUnavailable(moveIn.getBedId()));
        if (!bedAvailabilityService.isAssignable(bed)) {
            throw bedUnavailable(bed.getId());
        }

        try {
            occupancyRepository.save(new OccupancyEntity(
                    UUID.randomUUID(),
                    tenancy.getId(),
                    bed.getId(),
                    "ACTIVE",
                    now,
                    null,
                    now,
                    now
            ));
            occupancyRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw bedUnavailable(bed.getId());
        }

        activateSelectedEnrollments(moveIn);

        moveIn.setStatus("COMPLETED");
        moveIn.setUpdatedAt(now);
    }

    private void replaceServiceSelections(
            UUID moveInId,
            UUID propertyId,
            List<MoveInServiceSelectionRequest> selections,
            Instant now
    ) {
        moveInServiceSelectionRepository.deleteByMoveInId(moveInId);
        if (selections == null || selections.isEmpty()) {
            return;
        }

        Set<UUID> seen = new LinkedHashSet<>();
        List<MoveInServiceSelectionEntity> rows = new ArrayList<>();
        for (MoveInServiceSelectionRequest selection : selections) {
            if (selection == null || selection.serviceId() == null) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "serviceId is required for each service selection"
                );
            }
            if (!seen.add(selection.serviceId())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate service selection"
                );
            }
            ServiceEntity service = serviceCatalogRepository.findById(selection.serviceId())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.NOT_FOUND,
                            HttpStatus.NOT_FOUND,
                            "Service not found"
                    ));
            if (!service.getPropertyId().equals(propertyId) || !"ACTIVE".equals(service.getStatus())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Service is not available on this property"
                );
            }
            BigDecimal selectedAmount = selection.selectedAmount() == null
                    ? null
                    : scaleMoney(selection.selectedAmount());
            rows.add(new MoveInServiceSelectionEntity(
                    UUID.randomUUID(),
                    moveInId,
                    service.getId(),
                    selectedAmount,
                    now
            ));
        }
        moveInServiceSelectionRepository.saveAll(rows);
    }

    private void activateSelectedEnrollments(MoveInEntity moveIn) {
        List<MoveInServiceSelectionEntity> selections =
                moveInServiceSelectionRepository.findByMoveInId(moveIn.getId());
        for (MoveInServiceSelectionEntity selection : selections) {
            serviceEnrollmentService.activateForMoveIn(
                    moveIn.getTenancyId(),
                    selection.getServiceId(),
                    moveIn.getMoveInDate()
            );
        }
    }

    private MoveInEntity requireMoveIn(UUID moveInId) {
        return moveInRepository.findById(moveInId)
                .orElseThrow(this::notFound);
    }

    private void assertDraft(MoveInEntity moveIn) {
        if (!"DRAFT".equals(moveIn.getStatus())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Only DRAFT move-ins can be modified"
            );
        }
    }

    private TenancyEntity requireTenancy(UUID tenancyId) {
        return tenancyRepository.findById(tenancyId)
                .orElseThrow(this::notFound);
    }

    private void requireReadableScope(AppUserEntity user, UUID propertyId) {
        try {
            propertyAccessService.requireReadableProperty(user, propertyId);
        } catch (ApiException ex) {
            throw notFound();
        }
    }

    private void requireMutatorScope(AppUserEntity user, UUID propertyId) {
        try {
            propertyAccessService.requireInventoryMutator(user, propertyId);
        } catch (ApiException ex) {
            throw notFound();
        }
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Move-in not found");
    }

    private static ApiException bedUnavailable(UUID bedId) {
        return new ApiException(
                ErrorCode.BED_UNAVAILABLE,
                HttpStatus.CONFLICT,
                "The selected bed is no longer available.",
                Map.of("bedId", bedId.toString())
        );
    }

    private TenantEntity requireTenantInOrg(UUID tenantId, UUID organizationId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found"));
        if (!tenant.getOrganizationId().equals(organizationId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Tenant is not in this organization");
        }
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Tenant is not active");
        }
        return tenant;
    }

    private void requireActiveBedOnProperty(UUID bedId, UUID propertyId) {
        BedEntity bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
        if (!"ACTIVE".equals(bed.getStatus())) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found");
        }
        RoomEntity room = roomRepository.findById(bed.getRoomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Bed not found"));
        if (!room.getPropertyId().equals(propertyId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Bed is not on this property");
        }
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

    private MoveInResponse toResponse(MoveInEntity moveIn, TenancyEntity tenancy, UUID tenantId) {
        List<MoveInServiceSelectionResponse> selections = moveInServiceSelectionRepository
                .findByMoveInId(moveIn.getId())
                .stream()
                .map(s -> new MoveInServiceSelectionResponse(s.getServiceId(), s.getSelectedAmount()))
                .toList();
        return new MoveInResponse(
                moveIn.getId(),
                tenancy.getId(),
                tenancy.getStatus(),
                tenantId,
                tenancy.getPropertyId(),
                moveIn.getBedId(),
                moveIn.getMoveInDate(),
                moveIn.getStatus(),
                selections,
                moveIn.getCreatedAt(),
                moveIn.getUpdatedAt()
        );
    }

    private static PaymentResponse toPaymentResponse(PaymentEntity payment) {
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
                List.of()
        );
    }
}
