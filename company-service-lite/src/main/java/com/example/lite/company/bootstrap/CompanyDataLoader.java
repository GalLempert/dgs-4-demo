package com.example.lite.company.bootstrap;

import com.example.lite.company.service.CompanyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Seeds two demo companies at startup, through the service layer. */
@Component
public class CompanyDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataLoader.class);

    private final CompanyService companyService;

    public CompanyDataLoader(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Override
    public void run(String... args) {
        companyService.createCompany("Initech", "Software");
        companyService.createCompany("Acme", "Manufacturing");
        log.info("Seeded {} demo companies", companyService.getAllCompanies().size());
    }
}
