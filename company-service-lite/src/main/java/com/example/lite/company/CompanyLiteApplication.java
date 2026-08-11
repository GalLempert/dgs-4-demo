package com.example.lite.company;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone entry point of the lite company service (default port 8083). Scans
 * {@code com.example.lite} to pick up the lite infrastructure, exactly like
 * {@code PersonLiteApplication} - the two services share the framework, never a
 * runtime.
 */
@SpringBootApplication(scanBasePackages = "com.example.lite")
public class CompanyLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyLiteApplication.class, args);
    }
}
