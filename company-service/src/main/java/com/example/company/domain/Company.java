package com.example.company.domain;

import com.example.infrastructure.persistence.ReplicatedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Deliberately flat second demo aggregate: enough fields to filter on, nothing else.
 * Extending {@link ReplicatedEntity} is the only step needed for the company table to
 * be replicable through the {@code companiesBySequence} feed.
 */
@Entity
@Table(name = "company")
public class Company extends ReplicatedEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 64)
    private String industry;

    @Column(length = 64)
    private String city;

    private Integer employeeCount;

    private Integer foundedYear;

    protected Company() {
        // for JPA
    }

    public Company(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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
