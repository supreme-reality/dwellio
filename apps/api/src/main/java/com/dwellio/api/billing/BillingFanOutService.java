package com.dwellio.api.billing;

import com.dwellio.api.tenant.OccupancyRepository;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BillingFanOutService {

    private final BillingRunRepository billingRunRepository;
    private final BillingRunItemRepository billingRunItemRepository;
    private final TenancyRepository tenancyRepository;
    private final OccupancyRepository occupancyRepository;
    private final InvoiceJobPublisher invoiceJobPublisher;

    public BillingFanOutService(
            BillingRunRepository billingRunRepository,
            BillingRunItemRepository billingRunItemRepository,
            TenancyRepository tenancyRepository,
            OccupancyRepository occupancyRepository,
            InvoiceJobPublisher invoiceJobPublisher
    ) {
        this.billingRunRepository = billingRunRepository;
        this.billingRunItemRepository = billingRunItemRepository;
        this.tenancyRepository = tenancyRepository;
        this.occupancyRepository = occupancyRepository;
        this.invoiceJobPublisher = invoiceJobPublisher;
    }

    /**
     * Idempotent monthly fan-out: one billing_run per period; one item per active stay.
     * Does not create invoices — that is the invoice worker's job.
     */
    @Transactional
    public BillingFanOutResult fanOut(LocalDate billingPeriod) {
        LocalDate period = billingPeriod.withDayOfMonth(1);
        Instant now = Instant.now();

        BillingRunEntity run = billingRunRepository.findByBillingPeriod(period)
                .orElseGet(() -> billingRunRepository.save(new BillingRunEntity(
                        UUID.randomUUID(),
                        period,
                        "PENDING",
                        now,
                        now
                )));

        long existingItems = billingRunItemRepository.countByBillingRunId(run.getId());
        if (existingItems > 0) {
            return new BillingFanOutResult(run.getId(), period, existingItems, 0, true);
        }

        List<BillingRunItemEntity> items = new ArrayList<>();
        for (TenancyEntity tenancy : tenancyRepository.findAll()) {
            if (!"ACTIVE".equals(tenancy.getStatus())) {
                continue;
            }
            if (occupancyRepository.findByTenancyIdAndStatus(tenancy.getId(), "ACTIVE").isEmpty()) {
                continue;
            }
            items.add(new BillingRunItemEntity(
                    UUID.randomUUID(),
                    run.getId(),
                    tenancy.getId(),
                    "PENDING",
                    null,
                    null,
                    now,
                    now
            ));
        }
        billingRunItemRepository.saveAll(items);

        run.setStatus("COMPLETED");
        run.setUpdatedAt(now);
        billingRunRepository.save(run);

        invoiceJobPublisher.publishAll(items.stream()
                .map(item -> new InvoiceJob(item.getId(), item.getTenancyId(), period))
                .toList());

        return new BillingFanOutResult(run.getId(), period, items.size(), items.size(), false);
    }

    public record BillingFanOutResult(
            UUID billingRunId,
            LocalDate billingPeriod,
            long itemCount,
            long newlyEnqueued,
            boolean alreadyExisted
    ) {
    }
}
