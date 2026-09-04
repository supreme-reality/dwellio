package com.dwellio.invoiceworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Invoice worker — processes PENDING billing_run_item rows into MONTHLY invoices.
 */
@SpringBootApplication(scanBasePackages = "com.dwellio.api")
@EntityScan("com.dwellio.api")
@EnableJpaRepositories("com.dwellio.api")
public class InvoiceWorkerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(InvoiceWorkerApplication.class);
        app.setAdditionalProfiles("worker");
        app.run(args);
    }
}
