package com.dwellio.billingworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Billing worker entrypoint — fans out monthly billing_run_item rows.
 * SQS consumer wiring is added with Terraform/ECS; core logic lives in {@code com.dwellio.api.billing}.
 */
@SpringBootApplication(scanBasePackages = "com.dwellio.api")
@EntityScan("com.dwellio.api")
@EnableJpaRepositories("com.dwellio.api")
public class BillingWorkerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BillingWorkerApplication.class);
        app.setAdditionalProfiles("worker");
        app.run(args);
    }
}
