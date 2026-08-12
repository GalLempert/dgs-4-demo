package com.example.lite.person;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone entry point of the lite person service (default port 8082). Scans
 * {@code com.example.lite} so the lite infrastructure module
 * ({@code com.example.lite.graphql}) is picked up alongside this service's own
 * packages - the same relationship the full services have with
 * {@code graphql-infrastructure}, with none of its JPA machinery.
 */
@SpringBootApplication(scanBasePackages = "com.example.lite")
public class PersonLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonLiteApplication.class, args);
    }
}
