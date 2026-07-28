package com.example.company.dal;

import com.example.infrastructure.replication.ReplicatedDal;
import com.example.infrastructure.replication.ReplicatedDalSupport;
import com.example.company.domain.Company;
import org.springframework.stereotype.Component;

/**
 * The whole data access layer of the company domain is inherited from
 * {@link ReplicatedDal}; this class only names the resource and its database sequence.
 */
@Component
public class CompanyDal extends ReplicatedDal<Company> {

    public CompanyDal(CompanyRepository companyRepository, ReplicatedDalSupport support) {
        super("Company", "company_replication_seq", companyRepository, support);
    }
}
