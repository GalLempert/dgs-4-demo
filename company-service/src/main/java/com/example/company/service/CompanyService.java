package com.example.company.service;

import com.example.infrastructure.mapping.DeclarativeMapper;
import com.example.infrastructure.replication.ResourceService;
import com.example.company.dal.CompanyDal;
import com.example.company.domain.Company;
import com.example.company.service.dto.CompanyView;
import com.example.company.service.dto.CreateCompanyInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business layer of the company domain. Find / replication feed / count / max
 * sequence / soft delete are inherited from {@link ResourceService}; the
 * only domain-specific operation is creation, and even that is pure declarative
 * mapping (no calculated fields in this domain).
 */
@Service
public class CompanyService extends ResourceService<Company, CompanyView> {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyDal companyDal;
    private final DeclarativeMapper declarativeMapper;

    public CompanyService(CompanyDal companyDal, DeclarativeMapper declarativeMapper) {
        super(companyDal);
        this.companyDal = companyDal;
        this.declarativeMapper = declarativeMapper;
    }

    @Override
    protected CompanyView toView(Company company) {
        return declarativeMapper.map(company, CompanyView.class);
    }

    @Transactional
    public CompanyView createCompany(CreateCompanyInput input) {
        log.info("Creating company '{}'", input.getName());
        Company company = companyDal.save(declarativeMapper.map(input, Company.class));
        return toView(company);
    }
}
