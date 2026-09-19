package com.dwellio.api.billing;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Local/test-only billing trigger. Must never be enabled in deployed environments.
 */
@RestController
@RequestMapping("/api/v1/internal/billing")
@Profile({"local", "test"})
public class InternalBillingController {

    private final BillingFanOutService billingFanOutService;
    private final MonthlyInvoiceCreationService monthlyInvoiceCreationService;

    public InternalBillingController(
            BillingFanOutService billingFanOutService,
            MonthlyInvoiceCreationService monthlyInvoiceCreationService
    ) {
        this.billingFanOutService = billingFanOutService;
        this.monthlyInvoiceCreationService = monthlyInvoiceCreationService;
    }

    @PostMapping("/fan-out")
    public BillingFanOutService.BillingFanOutResult fanOut(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billingPeriod
    ) {
        return billingFanOutService.fanOut(billingPeriod);
    }

    @PostMapping("/process-pending")
    public MonthlyInvoiceCreationService.ProcessResult processPending() {
        return monthlyInvoiceCreationService.processPendingItems();
    }
}
