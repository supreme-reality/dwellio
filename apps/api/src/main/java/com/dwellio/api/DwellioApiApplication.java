package com.dwellio.api;

import com.dwellio.api.payment.RazorpayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RazorpayProperties.class)
public class DwellioApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DwellioApiApplication.class, args);
    }
}
