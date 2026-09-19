package com.dwellio.invoiceworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Invoice worker — polls PENDING billing_run_item rows into MONTHLY invoices.
 * SQS consumer replaces the poller when infra is ready.
 */
@SpringBootApplication(scanBasePackages = {"com.dwellio.api", "com.dwellio.invoiceworker"})
@EntityScan("com.dwellio.api")
@EnableJpaRepositories("com.dwellio.api")
@EnableScheduling
public class InvoiceWorkerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(InvoiceWorkerApplication.class);
        app.setAdditionalProfiles("worker");
        app.run(args);
    }
}
