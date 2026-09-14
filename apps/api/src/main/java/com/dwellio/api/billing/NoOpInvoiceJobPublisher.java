package com.dwellio.api.billing;

import org.springframework.stereotype.Component;

/**
 * Default publisher: leave items PENDING for the worker invoice-role poller.
 * Replace with SQS when infra is ready.
 */
@Component
public class NoOpInvoiceJobPublisher implements InvoiceJobPublisher {

    @Override
    public void publish(InvoiceJob job) {
        // no-op
    }
}
