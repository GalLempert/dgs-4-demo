package com.example.company.dal;

import com.example.infrastructure.replication.ResourceDal;
import com.example.infrastructure.replication.ResourceDalSupport;
import com.example.company.domain.Company;
import org.springframework.stereotype.Component;

/**
 * The whole standard data access layer of the company domain is inherited from
 * {@link ResourceDal}; this class names the resource and its database sequence and
 * adds the one domain-specific lookup (name uniqueness). Still the only class that
 * touches the repository.
 */
@Component
public class CompanyDal extends ResourceDal<Company> {

    private final CompanyRepository companyRepository;

    public CompanyDal(CompanyRepository companyRepository, ResourceDalSupport support) {
        super("Company", "company_replication_seq", companyRepository, support);
        this.companyRepository = companyRepository;
    }

    public boolean nameExists(String name) {
        return companyRepository.existsByNameIgnoreCase(name);
    }
}
