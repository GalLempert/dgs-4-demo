package com.example.company.service.dto;

import java.util.Set;

/**
 * Input DTO of the createCompany mutation; same shape as the entity, so the
 * declarative mapper carries every field with zero mapping code. {@code employeeIds}
 * are cross-service references: person ids owned by person-service, stored as bare
 * keys (see PersonRef).
 */
public class CreateCompanyInput {

    private String name;
    private String industry;
    private String city;
    private Integer employeeCount;
    private Integer foundedYear;
    private Set<Long> employeeIds;

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

    public Set<Long> getEmployeeIds() {
        return employeeIds;
    }

    public void setEmployeeIds(Set<Long> employeeIds) {
        this.employeeIds = employeeIds;
    }
}
