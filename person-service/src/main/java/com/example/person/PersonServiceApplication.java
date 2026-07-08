package com.example.person;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scans {@code com.example} so both the domain packages ({@code com.example.person}) and
 * the reusable infrastructure module ({@code com.example.infrastructure}) are picked up.
 */
@SpringBootApplication(scanBasePackages = "com.example")
public class PersonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonServiceApplication.class, args);
    }
}
