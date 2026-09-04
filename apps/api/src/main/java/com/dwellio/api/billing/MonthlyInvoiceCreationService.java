package com.dwellio.api.billing;

import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceLineItemEntity;
import com.dwellio.api.invoice.InvoiceLineItemRepository;
import com.dwellio.api.invoice.InvoiceRepository;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.property.PropertyRepository;
import com.dwellio.api.rent.RentConfigRepository;
import com.dwellio.api.rent.RentConfigEntity;
import com.dwellio.api.service.ServiceCatalogRepository;
import com.dwellio.api.service.ServiceChargeEntity;
import com.dwellio.api.service.ServiceChargeRepository;
import com.dwellio.api.service.ServiceConfigEntity;
import com.dwellio.api.service.ServiceConfigRepository;
import com.dwellio.api.service.ServiceEnrollmentEntity;
import com.dwellio.api.service.ServiceEnrollmentRepository;
import com.dwellio.api.service.ServiceEntity;
import com.dwellio.api.tenant.OccupancyEntity;
import com.dwellio.api.tenant.OccupancyRepository;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.inventory.BedEntity;
import com.dwellio.api.inventory.BedRepository;
import com.dwellio.api.inventory.RoomEntity;
import com.dwellio.api.inventory.RoomRepository;
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
public class MonthlyInvoiceCreationService {

    private final BillingRunItemRepository billingRunItemRepository;
    private final BillingRunRepository billingRunRepository;
    private final TenancyRepository tenancyRepository;
    private final OccupancyRepository occupancyRepository;
    private final PropertyRepository propertyRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final RentConfigRepository rentConfigRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final ServiceEnrollmentRepository enrollmentRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceChargeRepository chargeRepository;

    public MonthlyInvoiceCreationService(
            BillingRunItemRepository billingRunItemRepository,
            BillingRunRepository billingRunRepository,
            TenancyRepository tenancyRepository,
            OccupancyRepository occupancyRepository,
            PropertyRepository propertyRepository,
            BedRepository bedRepository,
            RoomRepository roomRepository,
            RentConfigRepository rentConfigRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineItemRepository invoiceLineItemRepository,
            ServiceEnrollmentRepository enrollmentRepository,
            ServiceCatalogRepository serviceCatalogRepository,
            ServiceConfigRepository serviceConfigRepository,
            ServiceChargeRepository chargeRepository
    ) {
        this.billingRunItemRepository = billingRunItemRepository;
        this.billingRunRepository = billingRunRepository;
        this.tenancyRepository = tenancyRepository;
        this.occupancyRepository = occupancyRepository;
        this.propertyRepository = propertyRepository;
        this.bedRepository = bedRepository;
        this.roomRepository = roomRepository;
        this.rentConfigRepository = rentConfigRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineItemRepository = invoiceLineItemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.serviceConfigRepository = serviceConfigRepository;
        this.chargeRepository = chargeRepository;
    }

    @Transactional
    public ProcessResult processPendingItems() {
        List<BillingRunItemEntity> pending = billingRunItemRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        int processed = 0;
        int skipped = 0;
        for (BillingRunItemEntity item : pending) {
            boolean created = processItem(item);
            if (created) {
                processed++;
            } else {
                skipped++;
            }
        }
        return new ProcessResult(processed, skipped);
    }

    @Transactional
    public boolean processItem(BillingRunItemEntity item) {
        if (!"PENDING".equals(item.getStatus())) {
            return false;
        }
        Instant now = Instant.now();
        BillingRunEntity run = billingRunRepository.findById(item.getBillingRunId())
                .orElseThrow(() -> new IllegalStateException("Billing run missing"));
        LocalDate period = run.getBillingPeriod();

        // Idempotent: existing MONTHLY for tenancy+period
        boolean exists = invoiceRepository.findByTenancyIdOrderByBillingDateDescCreatedAtDesc(item.getTenancyId())
                .stream()
                .anyMatch(i -> "MONTHLY".equals(i.getInvoiceType())
                        && period.equals(i.getBillingPeriod())
                        && !"VOID".equals(i.getStatus()));
        if (exists) {
            item.setStatus("SKIPPED");
            item.setUpdatedAt(now);
            billingRunItemRepository.save(item);
            return false;
        }

        TenancyEntity tenancy = tenancyRepository.findById(item.getTenancyId()).orElse(null);
        if (tenancy == null || !"ACTIVE".equals(tenancy.getStatus())) {
            item.setStatus("SKIPPED");
            item.setErrorMessage("Tenancy not active");
            item.setUpdatedAt(now);
            billingRunItemRepository.save(item);
            return false;
        }

        OccupancyEntity occupancy = occupancyRepository.findByTenancyIdAndStatus(tenancy.getId(), "ACTIVE")
                .orElse(null);
        if (occupancy == null) {
            item.setStatus("SKIPPED");
            item.setErrorMessage("No active occupancy");
            item.setUpdatedAt(now);
            billingRunItemRepository.save(item);
            return false;
        }

        PropertyEntity property = propertyRepository.findById(tenancy.getPropertyId()).orElseThrow();
        LocalDate billingDate = period;
        LocalDate dueDate = billingDate.plusDays(property.getPaymentDueDays());

        List<InvoiceLineItemEntity> lines = new ArrayList<>();
        UUID invoiceId = UUID.randomUUID();
        BigDecimal subtotal = zero();

        BigDecimal rent = resolveRent(property.getId(), occupancy.getBedId(), billingDate);
        if (rent.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new InvoiceLineItemEntity(
                    UUID.randomUUID(), invoiceId, "RENT", "Monthly rent",
                    BigDecimal.ONE, rent, rent, occupancy.getBedId(), now
            ));
            subtotal = subtotal.add(rent);
        }

        for (ServiceEnrollmentEntity enrollment :
                enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancy.getId())) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            ServiceEntity service = serviceCatalogRepository.findById(enrollment.getServiceId()).orElse(null);
            if (service == null || !"ACTIVE".equals(service.getStatus())) {
                continue;
            }
            if ("FIXED".equals(service.getBillingType()) && "MONTHLY_ARREARS".equals(service.getBillingTiming())) {
                BigDecimal amount = resolveServiceAmount(service.getId(), billingDate);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    lines.add(new InvoiceLineItemEntity(
                            UUID.randomUUID(), invoiceId, "SERVICE", service.getName(),
                            BigDecimal.ONE, amount, amount, service.getId(), now
                    ));
                    subtotal = subtotal.add(amount);
                }
            } else if ("VARIABLE".equals(service.getBillingType())) {
                BigDecimal amount = chargeRepository
                        .findByServiceEnrollmentIdAndBillingPeriod(enrollment.getId(), period)
                        .map(ServiceChargeEntity::getAmount)
                        .orElse(zero());
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    lines.add(new InvoiceLineItemEntity(
                            UUID.randomUUID(), invoiceId, "SERVICE", service.getName() + " (variable)",
                            BigDecimal.ONE, amount, amount, service.getId(), now
                    ));
                    subtotal = subtotal.add(amount);
                }
            }
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        InvoiceEntity invoice = invoiceRepository.save(new InvoiceEntity(
                invoiceId,
                tenancy.getId(),
                "MONTHLY",
                period,
                billingDate,
                dueDate,
                "FINALIZED",
                property.getDefaultCurrency(),
                subtotal,
                subtotal,
                now,
                now,
                now
        ));
        if (!lines.isEmpty()) {
            invoiceLineItemRepository.saveAll(lines);
        } else {
            invoiceLineItemRepository.save(new InvoiceLineItemEntity(
                    UUID.randomUUID(), invoiceId, "OTHER", "Monthly invoice (no charges)",
                    BigDecimal.ONE, zero(), zero(), null, now
            ));
        }

        item.setStatus("COMPLETED");
        item.setInvoiceId(invoice.getId());
        item.setUpdatedAt(now);
        billingRunItemRepository.save(item);
        return true;
    }

    private BigDecimal resolveRent(UUID propertyId, UUID bedId, LocalDate date) {
        BedEntity bed = bedRepository.findById(bedId).orElse(null);
        if (bed == null) {
            return zero();
        }
        RoomEntity room = roomRepository.findById(bed.getRoomId()).orElse(null);
        if (room == null) {
            return zero();
        }
        List<RentConfigEntity> bedConfigs = rentConfigRepository.findBedEffective(propertyId, bedId, date);
        if (!bedConfigs.isEmpty()) {
            return bedConfigs.getFirst().getAmount();
        }
        List<RentConfigEntity> roomConfigs = rentConfigRepository.findRoomEffective(propertyId, room.getId(), date);
        if (!roomConfigs.isEmpty()) {
            return roomConfigs.getFirst().getAmount();
        }
        List<RentConfigEntity> propertyConfigs = rentConfigRepository.findPropertyEffective(propertyId, date);
        if (propertyConfigs.isEmpty()) {
            return zero();
        }
        return propertyConfigs.getFirst().getAmount();
    }

    private BigDecimal resolveServiceAmount(UUID serviceId, LocalDate date) {
        return serviceConfigRepository.findByServiceIdOrderByEffectiveFromDesc(serviceId).stream()
                .filter(c -> !c.getEffectiveFrom().isAfter(date)
                        && (c.getEffectiveTo() == null || c.getEffectiveTo().isAfter(date)))
                .findFirst()
                .map(ServiceConfigEntity::getAmount)
                .orElse(zero());
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public record ProcessResult(int processed, int skipped) {
    }
}
