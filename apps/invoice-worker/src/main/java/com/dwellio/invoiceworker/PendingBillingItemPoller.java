package com.dwellio.invoiceworker;

import com.dwellio.api.billing.MonthlyInvoiceCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local/MVP adapter: poll PENDING billing_run_item rows.
 * Replace with SQS consumer when infra is ready; keep calling {@link MonthlyInvoiceCreationService}.
 */
@Component
@ConditionalOnProperty(name = "dwellio.invoice-worker.poll-enabled", havingValue = "true", matchIfMissing = true)
public class PendingBillingItemPoller {

    private static final Logger log = LoggerFactory.getLogger(PendingBillingItemPoller.class);

    private final MonthlyInvoiceCreationService monthlyInvoiceCreationService;

    public PendingBillingItemPoller(MonthlyInvoiceCreationService monthlyInvoiceCreationService) {
        this.monthlyInvoiceCreationService = monthlyInvoiceCreationService;
    }

    @Scheduled(fixedDelayString = "${dwellio.invoice-worker.poll-delay-ms:5000}")
    public void poll() {
        MonthlyInvoiceCreationService.ProcessResult result = monthlyInvoiceCreationService.processPendingItems();
        if (result.processed() > 0 || result.failed() > 0) {
            log.info(
                    "Invoice poll processed={} skipped={} failed={}",
                    result.processed(),
                    result.skipped(),
                    result.failed()
            );
        }
    }
}
