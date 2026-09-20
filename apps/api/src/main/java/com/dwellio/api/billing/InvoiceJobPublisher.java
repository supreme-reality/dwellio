package com.dwellio.api.billing;

import java.util.List;

/**
 * Publishes per-stay invoice jobs after fan-out.
 * Local/MVP: no-op — worker (WORKER_ROLE=invoice) polls PENDING items.
 * Later: SQS publisher with the same {@link InvoiceJob} payload.
 */
public interface InvoiceJobPublisher {

    void publish(InvoiceJob job);

    default void publishAll(List<InvoiceJob> jobs) {
        for (InvoiceJob job : jobs) {
            publish(job);
        }
    }
}
