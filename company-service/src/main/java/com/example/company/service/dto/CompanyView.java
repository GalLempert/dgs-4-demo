package com.example.company.service.dto;

import com.example.infrastructure.graphql.model.GraphQLModel;
import com.example.infrastructure.graphql.model.ResourceView;

/**
 * What the GraphQL layer exposes for a company. The technical fields (id, version,
 * createdAt, updatedAt, sequence, deleted) are inherited from
 * {@link ResourceView}; every field is carried by the declarative mapper -
 * the company domain has no hand-written mapping at all.
 */
@GraphQLModel("Company")
public class CompanyView extends ResourceView {

    private String name;
    private String industry;
    private String city;
    private Integer employeeCount;
    private Integer foundedYear;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(Integer employeeCount) {
        this.employeeCount = employeeCount;
    }

    public Integer getFoundedYear() {
        return foundedYear;
    }

    public void setFoundedYear(Integer foundedYear) {
        this.foundedYear = foundedYear;
    }
}
