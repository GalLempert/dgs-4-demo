package com.example.lite.company.service;

import com.example.lite.company.dal.CompanyDal;
import com.example.lite.company.domain.Company;
import org.springframework.stereotype.Service;

import java.util.List;

/** The business layer - GraphQL-free, called by the lambda fetchers in the config. */
@Service
public class CompanyService {

    private final CompanyDal companyDal;

    public CompanyService(CompanyDal companyDal) {
        this.companyDal = companyDal;
    }

    public List<Company> getAllCompanies() {
        return companyDal.findAll();
    }

    public Company getCompany(long id) {
        return companyDal.findById(id).orElse(null);
    }

    public Company createCompany(String name, String industry) {
        return companyDal.save(new Company(null, name, industry));
    }
}
