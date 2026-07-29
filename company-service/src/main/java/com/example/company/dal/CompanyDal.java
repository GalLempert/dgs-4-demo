package com.example.company.dal;

import com.example.infrastructure.replication.ResourceDal;
import com.example.infrastructure.replication.ResourceDalSupport;
import com.example.company.domain.Company;
import org.springframework.stereotype.Component;

/**
 * The whole data access layer of the company domain is inherited from
 * {@link ResourceDal}; this class only names the resource and its database sequence.
 */
@Component
public class CompanyDal extends ResourceDal<Company> {

    public CompanyDal(CompanyRepository companyRepository, ResourceDalSupport support) {
        super("Company", "company_replication_seq", companyRepository, support);
    }
}
