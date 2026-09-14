package com.dwellio.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shared worker entrypoint. Select role via {@code WORKER_ROLE} / {@code dwellio.worker.role}:
 * {@code billing} (fan-out / Billing Trigger queue) or {@code invoice} (MONTHLY invoices).
 * Deploy the same image as separate ECS services with different roles for independent scaling.
 * Domain logic lives in {@code com.dwellio.api}.
 */
@SpringBootApplication(scanBasePackages = {"com.dwellio.api", "com.dwellio.worker"})
@EntityScan("com.dwellio.api")
@EnableJpaRepositories("com.dwellio.api")
@EnableScheduling
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WorkerApplication.class);
        app.setAdditionalProfiles("worker");
        app.run(args);
    }
}
