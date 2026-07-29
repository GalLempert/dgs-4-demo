package com.example.company.service;

import com.example.infrastructure.error.DuplicateResourceException;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.mapping.DeclarativeMapper;
import com.example.infrastructure.reference.ResourceRef;
import com.example.infrastructure.replication.ResourceService;
import com.example.company.dal.CompanyDal;
import com.example.company.domain.Company;
import com.example.company.service.dto.CompanyView;
import com.example.company.service.dto.CreateCompanyInput;
import com.example.company.service.dto.PersonRef;
import org.springframework.stereotype.Service;

/**
 * Business layer of the company domain. Find / replication feed / count / max
 * sequence and ALL standard mutations (save-new, update-by-filter, save-or-update,
 * save-or-override, delete) are inherited from {@link ResourceService};
 * the only domain contributions are the entity-to-view mapping and the natural key
 * (a company is identified by its name, so new names must be unique).
 */
@Service
public class CompanyService extends ResourceService<Company, CompanyView> {

    private final CompanyDal companyDal;

    public CompanyService(CompanyDal companyDal, DeclarativeMapper declarativeMapper) {
        super(companyDal, declarativeMapper, Company.class);
        this.companyDal = companyDal;
    }

    @Override
    protected CompanyView toView(Company company) {
        CompanyView view = declarativeMapper().map(company, CompanyView.class);
        // cross-service references: stored person ids become id-only Person stubs
        view.setEmployees(ResourceRef.toRefs(company.getEmployeeIds(), PersonRef::new));
        return view;
    }

    /** What identifies "the same company" for saveOrOverride: the company name. */
    @Override
    protected FilterCriteria naturalKeyOf(Object input) {
        return FilterCriteria.whereEquals("name", ((CreateCompanyInput) input).getName());
    }

    /**
     * The name is the natural key, so it must identify at most one row: creation of a
     * second company with an existing name is rejected (backed by the database unique
     * constraint on the column).
     */
    @Override
    protected void validate(Company company) {
        if (company.getId() == null && companyDal.nameExists(company.getName())) {
            throw new DuplicateResourceException(
                    "A company named " + company.getName() + " already exists", "name");
        }
    }
}
