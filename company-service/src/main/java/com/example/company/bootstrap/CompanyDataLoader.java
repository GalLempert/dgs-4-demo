package com.example.company.bootstrap;

import com.example.company.service.CompanyService;
import com.example.company.service.dto.CreateCompanyInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a few demo companies at startup (through the inherited saveNew, so the same
 * code path as the createCompany mutation is exercised).
 */
@Component
public class CompanyDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataLoader.class);

    private final CompanyService companyService;

    public CompanyDataLoader(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Override
    public void run(String... args) {
        // employee ids are cross-service references to persons owned by person-service
        // (seeded there as ids 1-3); stored as bare keys, never validated here
        companyService.saveNew(company("Initech", "Software", "Tel Aviv", 320, 1997, 1L, 2L));
        companyService.saveNew(company("Globex", "Manufacturing", "Haifa", 1200, 1989, 3L));
        companyService.saveNew(company("Hooli", "Software", "Jerusalem", 5400, 2012));
        log.info("Seeded 3 demo companies");
    }

    private CreateCompanyInput company(String name, String industry, String city,
                                       int employeeCount, int foundedYear, Long... employeeIds) {
        CreateCompanyInput input = new CreateCompanyInput();
        input.setName(name);
        input.setIndustry(industry);
        input.setCity(city);
        input.setEmployeeCount(employeeCount);
        input.setFoundedYear(foundedYear);
        input.setEmployeeIds(new java.util.LinkedHashSet<>(java.util.Arrays.asList(employeeIds)));
        return input;
    }
}
