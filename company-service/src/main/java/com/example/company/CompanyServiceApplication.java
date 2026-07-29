package com.example.company;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Standalone entry point of the company service. Scans {@code com.example} so the
 * reusable infrastructure module ({@code com.example.infrastructure}) is picked up
 * alongside this service's own domain package; {@code @EntityScan} and
 * {@code @EnableJpaRepositories} widen JPA's scanning the same way.
 *
 * <p>Each domain is its own GraphQL service: this application serves only the company
 * schema and API (default port 8081), completely independent of person-service - the
 * two share the framework ("how"), never a runtime ("what").
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackages = "com.example")
public class CompanyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyServiceApplication.class, args);
    }
}
