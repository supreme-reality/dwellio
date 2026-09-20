package com.dwellio.api.checkout;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.deposit.DepositLedgerEntity;
import com.dwellio.api.deposit.DepositLedgerRepository;
import com.dwellio.api.deposit.DepositService;
import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceLineItemEntity;
import com.dwellio.api.invoice.InvoiceLineItemRepository;
import com.dwellio.api.invoice.InvoiceRepository;
import com.dwellio.api.payment.PaymentEntity;
import com.dwellio.api.payment.PaymentRepository;
import com.dwellio.api.payment.PaymentService;
import com.dwellio.api.payment.RazorpayCheckoutResponse;
import com.dwellio.api.payment.RazorpayOrderClient;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.rent.RentConfigService;
import com.dwellio.api.rent.ResolvedRentResponse;
import com.dwellio.api.service.ServiceChargeRepository;
import com.dwellio.api.service.ServiceConfigEntity;
import com.dwellio.api.service.ServiceConfigRepository;
import com.dwellio.api.service.ServiceEnrollmentEntity;
import com.dwellio.api.service.ServiceEnrollmentRepository;
import com.dwellio.api.service.ServiceCatalogRepository;
import com.dwellio.api.service.ServiceEntity;
import com.dwellio.api.settlement.SettlementSnapshotEntity;
import com.dwellio.api.settlement.SettlementSnapshotRepository;
import com.dwellio.api.tenant.OccupancyEntity;
import com.dwellio.api.tenant.OccupancyRepository;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckoutService {

    private final TenancyRepository tenancyRepository;
    private final OccupancyRepository occupancyRepository;
    private final PropertyAccessService propertyAccessService;
    private final DepositService depositService;
    private final DepositLedgerRepository depositLedgerRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final SettlementSnapshotRepository settlementSnapshotRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RentConfigService rentConfigService;
    private final ServiceEnrollmentRepository enrollmentRepository;
    private final ServiceChargeRepository chargeRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final RazorpayOrderClient razorpayOrderClient;

    private static final Set<String> COLLECT_METHODS = Set.of("CASH", "BANK_TRANSFER", "RAZORPAY");

    public CheckoutService(
            TenancyRepository tenancyRepository,
            OccupancyRepository occupancyRepository,
            PropertyAccessService propertyAccessService,
            DepositService depositService,
            DepositLedgerRepository depositLedgerRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineItemRepository invoiceLineItemRepository,
            SettlementSnapshotRepository settlementSnapshotRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            RentConfigService rentConfigService,
            ServiceEnrollmentRepository enrollmentRepository,
            ServiceChargeRepository chargeRepository,
            ServiceCatalogRepository serviceCatalogRepository,
            ServiceConfigRepository serviceConfigRepository,
            RazorpayOrderClient razorpayOrderClient
    ) {
        this.tenancyRepository = tenancyRepository;
        this.occupancyRepository = occupancyRepository;
        this.propertyAccessService = propertyAccessService;
        this.depositService = depositService;
        this.depositLedgerRepository = depositLedgerRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineItemRepository = invoiceLineItemRepository;
        this.settlementSnapshotRepository = settlementSnapshotRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.rentConfigService = rentConfigService;
        this.enrollmentRepository = enrollmentRepository;
        this.chargeRepository = chargeRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.serviceConfigRepository = serviceConfigRepository;
        this.razorpayOrderClient = razorpayOrderClient;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(AppUserEntity user, CheckoutPreviewRequest request) {
        CheckoutContext ctx = loadActiveContext(user, request.tenancyId());
        SettlementMath math = computeMath(user, ctx, request.damagesAmount(), request.manualChargesAmount());
        return toPreview(ctx, math);
    }

    @Transactional
    public CheckoutConfirmResponse confirm(AppUserEntity user, CheckoutConfirmRequest request) {
        CheckoutContext ctx = loadActiveContext(user, request.tenancyId());
        if (settlementSnapshotRepository.existsByTenancyId(ctx.tenancy().getId())) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Checkout already confirmed");
        }

        SettlementMath math = computeMath(user, ctx, request.damagesAmount(), request.manualChargesAmount());
        boolean leaveReceivable = Boolean.TRUE.equals(request.leaveReceivable());
        String paymentMethod = blankToNull(request.paymentMethod());
        if (paymentMethod != null) {
            paymentMethod = paymentMethod.toUpperCase(Locale.ROOT);
        }
        boolean collecting = paymentMethod != null;

        if (math.netReceivable().compareTo(BigDecimal.ZERO) > 0) {
            if (leaveReceivable && collecting) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Cannot leave receivable unpaid and collect payment"
                );
            }
            if (!leaveReceivable && !collecting) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "leaveReceivable must be true when netReceivable > 0"
                );
            }
            if (collecting && !COLLECT_METHODS.contains(paymentMethod)) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unsupported payment method"
                );
            }
            if ("BANK_TRANSFER".equals(paymentMethod)
                    && (request.bankTransferReference() == null || request.bankTransferReference().isBlank())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Bank transfer reference is required"
                );
            }
        }

        Instant now = Instant.now();
        LocalDate checkoutDate = LocalDate.now();

        InvoiceEntity checkoutInvoice = createCheckoutInvoice(ctx, math, checkoutDate, now);

        UUID settlementId = UUID.randomUUID();
        SettlementSnapshotEntity snapshot = settlementSnapshotRepository.save(new SettlementSnapshotEntity(
                settlementId,
                ctx.tenancy().getId(),
                ctx.occupancy().getId(),
                checkoutDate,
                math.outstandingReceivables(),
                math.newCheckoutCharges(),
                math.depositBalanceBefore(),
                math.depositDeduction(),
                math.depositRefundDue(),
                math.totalReceivable(),
                math.netReceivable(),
                math.refundDue(),
                ctx.property().getDefaultCurrency(),
                "CONFIRMED",
                now,
                now
        ));

        if (math.depositDeduction().compareTo(BigDecimal.ZERO) > 0) {
            depositLedgerRepository.save(new DepositLedgerEntity(
                    UUID.randomUUID(),
                    ctx.tenancy().getId(),
                    "DEDUCTION",
                    math.depositDeduction(),
                    settlementId.toString(),
                    "Checkout deposit deduction",
                    now
            ));
        }

        RazorpayCheckoutResponse razorpay = null;
        if (collecting && math.netReceivable().compareTo(BigDecimal.ZERO) > 0) {
            razorpay = collectNetReceivable(user, ctx, request, math, paymentMethod, now);
        } else if (math.totalReceivable().compareTo(BigDecimal.ZERO) > 0) {
            // Deposit covers all, or unpaid finish: one confirmed CASH row for full owed.
            // Snapshot.netReceivable remains the true cash still owed when leaveReceivable.
            PaymentEntity settlementPayment = paymentRepository.save(new PaymentEntity(
                    UUID.randomUUID(),
                    ctx.property().getOrganizationId(),
                    ctx.tenancy().getId(),
                    "CASH",
                    math.totalReceivable(),
                    ctx.property().getDefaultCurrency(),
                    "CONFIRMED",
                    "CHECKOUT_SETTLEMENT:" + settlementId,
                    null,
                    null,
                    null,
                    now,
                    now,
                    now
            ));
            paymentService.settleUnpaidForPayment(settlementPayment, user);
        }

        OccupancyEntity occupancy = ctx.occupancy();
        occupancy.setStatus("COMPLETED");
        occupancy.setEndedAt(now);
        occupancy.setUpdatedAt(now);
        occupancyRepository.save(occupancy);

        TenancyEntity tenancy = ctx.tenancy();
        tenancy.setStatus("CHECKED_OUT");
        tenancy.setUpdatedAt(now);
        tenancyRepository.save(tenancy);

        endActiveEnrollments(ctx.tenancy().getId(), checkoutDate, now);

        return new CheckoutConfirmResponse(
                snapshot.getId(),
                snapshot.getTenancyId(),
                checkoutInvoice.getId(),
                snapshot.getOutstandingReceivable(),
                snapshot.getNewCheckoutCharges(),
                snapshot.getDepositBalanceBefore(),
                snapshot.getDepositDeduction(),
                snapshot.getDepositRefundDue(),
                snapshot.getTotalReceivable(),
                snapshot.getNetReceivable(),
                snapshot.getRefundDue(),
                snapshot.getCurrency(),
                razorpay
        );
    }

    private RazorpayCheckoutResponse collectNetReceivable(
            AppUserEntity user,
            CheckoutContext ctx,
            CheckoutConfirmRequest request,
            SettlementMath math,
            String paymentMethod,
            Instant now
    ) {
        String currency = ctx.property().getDefaultCurrency();
        String idempotencyKey = blankToNull(request.idempotencyKey());

        if ("RAZORPAY".equals(paymentMethod)) {
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

            UUID paymentId = UUID.randomUUID();
            RazorpayOrderClient.CreatedOrder order = razorpayOrderClient.createOrder(
                    math.netReceivable(),
                    currency,
                    "chk-" + paymentId.toString().replace("-", ""),
                    Map.of("dwellioPaymentId", paymentId.toString())
            );
            paymentRepository.save(new PaymentEntity(
                    paymentId,
                    ctx.property().getOrganizationId(),
                    ctx.tenancy().getId(),
                    "RAZORPAY",
                    math.netReceivable(),
                    currency,
                    "PENDING",
                    order.orderId(),
                    null,
                    idempotencyKey,
                    null,
                    null,
                    now,
                    now
            ));
            return new RazorpayCheckoutResponse(order.keyId(), order.orderId(), math.netReceivable(), currency);
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

        PaymentEntity collected = paymentRepository.save(new PaymentEntity(
                UUID.randomUUID(),
                ctx.property().getOrganizationId(),
                ctx.tenancy().getId(),
                paymentMethod,
                math.netReceivable(),
                currency,
                "CONFIRMED",
                null,
                blankToNull(request.bankTransferReference()),
                idempotencyKey,
                null,
                now,
                now,
                now
        ));
        paymentService.settleUnpaidForPayment(collected, user);
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(AppUserEntity user, UUID settlementId) {
        SettlementSnapshotEntity snapshot = settlementSnapshotRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Settlement not found"));
        TenancyEntity tenancy = tenancyRepository.findById(snapshot.getTenancyId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Settlement not found"));
        try {
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Settlement not found");
        }
        return new SettlementResponse(
                snapshot.getId(),
                snapshot.getTenancyId(),
                snapshot.getOccupancyId(),
                snapshot.getCheckoutDate(),
                snapshot.getOutstandingReceivable(),
                snapshot.getNewCheckoutCharges(),
                snapshot.getDepositBalanceBefore(),
                snapshot.getDepositDeduction(),
                snapshot.getDepositRefundDue(),
                snapshot.getTotalReceivable(),
                snapshot.getNetReceivable(),
                snapshot.getRefundDue(),
                snapshot.getCurrency(),
                snapshot.getStatus(),
                snapshot.getConfirmedAt(),
                snapshot.getCreatedAt()
        );
    }

    @Transactional
    public SettlementRefundResponse refund(AppUserEntity user, UUID settlementId, SettlementRefundRequest request) {
        SettlementSnapshotEntity snapshot = settlementSnapshotRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Settlement not found"));
        TenancyEntity tenancy = tenancyRepository.findById(snapshot.getTenancyId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Settlement not found"));
        propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());

        if (snapshot.getRefundDue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No refundable deposit on this settlement"
            );
        }

        BigDecimal alreadyRefunded = depositLedgerRepository.findByTenancyIdOrderByCreatedAtAsc(tenancy.getId())
                .stream()
                .filter(e -> "REFUND".equals(e.getType()) && settlementId.toString().equals(e.getReference()))
                .map(DepositLedgerEntity::getAmount)
                .reduce(zero(), BigDecimal::add);

        BigDecimal remaining = snapshot.getRefundDue().subtract(alreadyRefunded).setScale(2, RoundingMode.HALF_UP);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Settlement refund already paid"
            );
        }

        String method = request.paymentMethod().trim().toUpperCase();
        if ("BANK_TRANSFER".equals(method)
                && (request.bankTransferReference() == null || request.bankTransferReference().isBlank())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bank transfer reference is required"
            );
        }

        BigDecimal liveBalance = depositService.balanceOf(tenancy.getId()).setScale(2, RoundingMode.HALF_UP);
        if (remaining.compareTo(liveBalance) > 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Refund exceeds remaining deposit balance"
            );
        }

        Instant now = Instant.now();
        DepositLedgerEntity refund = depositLedgerRepository.save(new DepositLedgerEntity(
                UUID.randomUUID(),
                tenancy.getId(),
                "REFUND",
                remaining,
                settlementId.toString(),
                "Deposit refund via " + method
                        + (request.bankTransferReference() != null
                        ? " ref=" + request.bankTransferReference().trim()
                        : ""),
                now
        ));

        return new SettlementRefundResponse(
                settlementId,
                tenancy.getId(),
                refund.getId(),
                remaining,
                method,
                depositService.balanceOf(tenancy.getId()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private CheckoutContext loadActiveContext(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());
        if (!"ACTIVE".equals(tenancy.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Tenancy is not active");
        }
        OccupancyEntity occupancy = occupancyRepository.findByTenancyIdAndStatus(tenancyId, "ACTIVE")
                .orElseThrow(() -> new ApiException(
                        ErrorCode.CONFLICT,
                        HttpStatus.CONFLICT,
                        "No active occupancy for tenancy"
                ));
        return new CheckoutContext(tenancy, property, occupancy);
    }

    private SettlementMath computeMath(
            AppUserEntity user,
            CheckoutContext ctx,
            BigDecimal damagesAmount,
            BigDecimal manualChargesAmount
    ) {
        BigDecimal outstanding = invoiceRepository
                .findUnsettledFinalizedByTenancyAndCurrency(ctx.tenancy().getId(), ctx.property().getDefaultCurrency())
                .stream()
                .map(InvoiceEntity::getTotal)
                .reduce(zero(), BigDecimal::add);

        BigDecimal damages = moneyOrZero(damagesAmount);
        BigDecimal manual = moneyOrZero(manualChargesAmount);
        LocalDate period = LocalDate.now().withDayOfMonth(1);
        boolean hasMonthly = invoiceRepository.existsFinalizedMonthly(ctx.tenancy().getId(), period);

        BigDecimal rentProration = zero();
        BigDecimal variable = zero();
        BigDecimal fixedArrears = zero();
        List<UnbilledVariable> unbilledVariables = List.of();

        if (!hasMonthly) {
            rentProration = computeRentProration(user, ctx);
            unbilledVariables = collectUnbilledVariableCharges(ctx.tenancy().getId(), period);
            variable = unbilledVariables.stream()
                    .map(UnbilledVariable::amount)
                    .reduce(zero(), BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            fixedArrears = computeUnbilledFixedMonthlyArrears(ctx.tenancy().getId(), period);
        }

        BigDecimal newCharges = rentProration
                .add(damages)
                .add(manual)
                .add(variable)
                .add(fixedArrears)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal depositBefore = depositService.balanceOf(ctx.tenancy().getId()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReceivable = outstanding.add(newCharges).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deduction = depositBefore.min(totalReceivable).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netReceivable = totalReceivable.subtract(deduction).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundDue = depositBefore.subtract(deduction).setScale(2, RoundingMode.HALF_UP);

        return new SettlementMath(
                outstanding,
                newCharges,
                rentProration,
                damages,
                manual,
                variable,
                fixedArrears,
                unbilledVariables,
                depositBefore,
                deduction,
                refundDue,
                totalReceivable,
                netReceivable,
                refundDue
        );
    }

    private BigDecimal computeRentProration(AppUserEntity user, CheckoutContext ctx) {
        LocalDate today = LocalDate.now();
        try {
            ResolvedRentResponse rent = rentConfigService.resolve(
                    user,
                    ctx.property().getId(),
                    ctx.occupancy().getBedId(),
                    today
            );
            int daysInMonth = today.lengthOfMonth();
            int daysUsed = today.getDayOfMonth();
            return rent.amount()
                    .multiply(BigDecimal.valueOf(daysUsed))
                    .divide(BigDecimal.valueOf(daysInMonth), 8, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (ApiException ex) {
            if (ex.getCode() == ErrorCode.NOT_FOUND) {
                return zero();
            }
            throw ex;
        }
    }

    private List<UnbilledVariable> collectUnbilledVariableCharges(UUID tenancyId, LocalDate period) {
        List<UnbilledVariable> result = new ArrayList<>();
        for (ServiceEnrollmentEntity enrollment :
                enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId)) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            ServiceEntity service = serviceCatalogRepository.findById(enrollment.getServiceId()).orElse(null);
            if (service == null || !"VARIABLE".equals(service.getBillingType())) {
                continue;
            }
            chargeRepository.findByServiceEnrollmentIdAndBillingPeriod(enrollment.getId(), period)
                    .ifPresent(charge -> {
                        if (charge.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                            return;
                        }
                        if (invoiceLineItemRepository.existsFinalizedByReferenceId(charge.getId())) {
                            return;
                        }
                        result.add(new UnbilledVariable(
                                charge.getId(),
                                service.getName() + " (variable)",
                                charge.getAmount().setScale(2, RoundingMode.HALF_UP)
                        ));
                    });
        }
        return result;
    }

    private BigDecimal computeUnbilledFixedMonthlyArrears(UUID tenancyId, LocalDate period) {
        BigDecimal total = zero();
        for (ServiceEnrollmentEntity enrollment :
                enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId)) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            ServiceEntity service = serviceCatalogRepository.findById(enrollment.getServiceId()).orElse(null);
            if (service == null
                    || !"ACTIVE".equals(service.getStatus())
                    || !"FIXED".equals(service.getBillingType())
                    || !"MONTHLY_ARREARS".equals(service.getBillingTiming())) {
                continue;
            }
            BigDecimal amount = serviceConfigRepository.findByServiceIdOrderByEffectiveFromDesc(service.getId()).stream()
                    .filter(c -> !c.getEffectiveFrom().isAfter(period)
                            && (c.getEffectiveTo() == null || c.getEffectiveTo().isAfter(period)))
                    .findFirst()
                    .map(ServiceConfigEntity::getAmount)
                    .orElse(zero());
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(amount);
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceEntity createCheckoutInvoice(
            CheckoutContext ctx,
            SettlementMath math,
            LocalDate checkoutDate,
            Instant now
    ) {
        BigDecimal amount = math.newCheckoutCharges();
        InvoiceEntity invoice = invoiceRepository.save(new InvoiceEntity(
                UUID.randomUUID(),
                ctx.tenancy().getId(),
                "CHECKOUT",
                checkoutDate.withDayOfMonth(1),
                checkoutDate,
                checkoutDate.plusDays(ctx.property().getPaymentDueDays()),
                "FINALIZED",
                ctx.property().getDefaultCurrency(),
                amount,
                amount,
                now,
                now,
                now
        ));

        List<InvoiceLineItemEntity> lines = new ArrayList<>();
        if (math.rentProration().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "RENT", "Checkout rent proration", math.rentProration(), null, now));
        }
        for (UnbilledVariable variable : math.unbilledVariables()) {
            lines.add(line(
                    invoice.getId(),
                    "SERVICE",
                    variable.description(),
                    variable.amount(),
                    variable.chargeId(),
                    now
            ));
        }
        if (math.fixedMonthlyArrearsAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(
                    invoice.getId(),
                    "SERVICE",
                    "Unbilled fixed monthly arrears",
                    math.fixedMonthlyArrearsAmount(),
                    null,
                    now
            ));
        }
        if (math.damagesAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "DAMAGE", "Checkout damages", math.damagesAmount(), null, now));
        }
        if (math.manualChargesAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "OTHER", "Checkout manual charges", math.manualChargesAmount(), null, now));
        }
        if (lines.isEmpty() && amount.compareTo(BigDecimal.ZERO) == 0) {
            lines.add(line(invoice.getId(), "OTHER", "Checkout (no new charges)", zero(), null, now));
        }
        invoiceLineItemRepository.saveAll(lines);
        return invoice;
    }

    private static InvoiceLineItemEntity line(
            UUID invoiceId,
            String type,
            String description,
            BigDecimal amount,
            UUID referenceId,
            Instant now
    ) {
        return new InvoiceLineItemEntity(
                UUID.randomUUID(),
                invoiceId,
                type,
                description,
                BigDecimal.ONE,
                amount,
                amount,
                referenceId,
                now
        );
    }

    private void endActiveEnrollments(UUID tenancyId, LocalDate endedAt, Instant now) {
        for (ServiceEnrollmentEntity enrollment :
                enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId)) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            enrollment.setStatus("ENDED");
            enrollment.setEndedAt(endedAt);
            enrollment.setUpdatedAt(now);
            enrollmentRepository.save(enrollment);
        }
    }

    private CheckoutPreviewResponse toPreview(CheckoutContext ctx, SettlementMath math) {
        return new CheckoutPreviewResponse(
                ctx.tenancy().getId(),
                ctx.occupancy().getId(),
                math.outstandingReceivables(),
                math.newCheckoutCharges(),
                math.rentProration(),
                math.damagesAmount(),
                math.manualChargesAmount(),
                math.variableChargesAmount(),
                math.fixedMonthlyArrearsAmount(),
                math.depositBalanceBefore(),
                math.depositDeduction(),
                math.depositRefundDue(),
                math.totalReceivable(),
                math.netReceivable(),
                math.refundDue(),
                ctx.property().getDefaultCurrency()
        );
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private record CheckoutContext(TenancyEntity tenancy, PropertyEntity property, OccupancyEntity occupancy) {
    }

    private record UnbilledVariable(UUID chargeId, String description, BigDecimal amount) {
    }

    private record SettlementMath(
            BigDecimal outstandingReceivables,
            BigDecimal newCheckoutCharges,
            BigDecimal rentProration,
            BigDecimal damagesAmount,
            BigDecimal manualChargesAmount,
            BigDecimal variableChargesAmount,
            BigDecimal fixedMonthlyArrearsAmount,
            List<UnbilledVariable> unbilledVariables,
            BigDecimal depositBalanceBefore,
            BigDecimal depositDeduction,
            BigDecimal depositRefundDue,
            BigDecimal totalReceivable,
            BigDecimal netReceivable,
            BigDecimal refundDue
    ) {
    }
}
