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
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.rent.RentConfigService;
import com.dwellio.api.rent.ResolvedRentResponse;
import com.dwellio.api.service.ServiceChargeEntity;
import com.dwellio.api.service.ServiceChargeRepository;
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
            ServiceCatalogRepository serviceCatalogRepository
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
        if (math.netReceivable().compareTo(BigDecimal.ZERO) > 0 && !leaveReceivable) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "leaveReceivable must be true when netReceivable > 0"
            );
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

        // Clear invoice obligations at checkout: one confirmed payment for full owed (deposit + any leaveReceivable).
        // Snapshot.netReceivable remains the true cash still owed by the tenant.
        BigDecimal owed = math.totalReceivable();
        if (owed.compareTo(BigDecimal.ZERO) > 0) {
            PaymentEntity settlementPayment = paymentRepository.save(new PaymentEntity(
                    UUID.randomUUID(),
                    ctx.property().getOrganizationId(),
                    ctx.tenancy().getId(),
                    "CASH",
                    owed,
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
                snapshot.getCurrency()
        );
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
        BigDecimal rentProration = computeRentProration(user, ctx);
        BigDecimal variable = computeUnbilledVariableCharges(ctx.tenancy().getId());
        BigDecimal newCharges = rentProration.add(damages).add(manual).add(variable).setScale(2, RoundingMode.HALF_UP);

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
        LocalDate periodStart = today.withDayOfMonth(1);
        boolean monthlyExists = invoiceRepository
                .findByTenancyIdOrderByBillingDateDescCreatedAtDesc(ctx.tenancy().getId())
                .stream()
                .anyMatch(i -> "MONTHLY".equals(i.getInvoiceType())
                        && "FINALIZED".equals(i.getStatus())
                        && periodStart.equals(i.getBillingPeriod()));
        if (monthlyExists) {
            return zero();
        }
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

    private BigDecimal computeUnbilledVariableCharges(UUID tenancyId) {
        LocalDate period = LocalDate.now().withDayOfMonth(1);
        BigDecimal total = zero();
        List<ServiceEnrollmentEntity> enrollments =
                enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId);
        for (ServiceEnrollmentEntity enrollment : enrollments) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            ServiceEntity service = serviceCatalogRepository.findById(enrollment.getServiceId()).orElse(null);
            if (service == null || !"VARIABLE".equals(service.getBillingType())) {
                continue;
            }
            total = total.add(
                    chargeRepository.findByServiceEnrollmentIdAndBillingPeriod(enrollment.getId(), period)
                            .map(ServiceChargeEntity::getAmount)
                            .orElse(zero())
            );
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
            lines.add(line(invoice.getId(), "RENT", "Checkout rent proration", math.rentProration(), now));
        }
        if (math.variableChargesAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "SERVICE", "Unbilled variable services", math.variableChargesAmount(), now));
        }
        if (math.damagesAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "DAMAGE", "Checkout damages", math.damagesAmount(), now));
        }
        if (math.manualChargesAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(invoice.getId(), "OTHER", "Checkout manual charges", math.manualChargesAmount(), now));
        }
        if (lines.isEmpty() && amount.compareTo(BigDecimal.ZERO) == 0) {
            lines.add(line(invoice.getId(), "OTHER", "Checkout (no new charges)", zero(), now));
        }
        invoiceLineItemRepository.saveAll(lines);
        return invoice;
    }

    private static InvoiceLineItemEntity line(
            UUID invoiceId,
            String type,
            String description,
            BigDecimal amount,
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
                null,
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

    private record SettlementMath(
            BigDecimal outstandingReceivables,
            BigDecimal newCheckoutCharges,
            BigDecimal rentProration,
            BigDecimal damagesAmount,
            BigDecimal manualChargesAmount,
            BigDecimal variableChargesAmount,
            BigDecimal depositBalanceBefore,
            BigDecimal depositDeduction,
            BigDecimal depositRefundDue,
            BigDecimal totalReceivable,
            BigDecimal netReceivable,
            BigDecimal refundDue
    ) {
    }
}
