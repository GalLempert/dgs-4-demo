package com.example.person;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans {@code com.example} so the reusable infrastructure module
 * ({@code com.example.infrastructure}) and every contributed domain module
 * ({@code com.example.person}, {@code com.example.company}, ...) are picked up.
 * {@code @EntityScan} and {@code @EnableJpaRepositories} widen JPA's scanning the same
 * way - by default those only cover this class's own package, which would hide the
 * entities and repositories of sibling domain modules.
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackages = "com.example")
public class PersonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonServiceApplication.class, args);
    }
}
