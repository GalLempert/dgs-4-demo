package com.example.company.service.dto;

import com.example.infrastructure.graphql.model.GraphQLModel;
import com.example.infrastructure.graphql.model.GraphQLTemporal;

import java.time.LocalDateTime;

/**
 * What the GraphQL layer exposes for a company. Every field is carried by the
 * declarative mapper - the company domain has no hand-written mapping at all.
 */
@GraphQLModel("Company")
public class CompanyView {

    private Long id;
    private String name;
    private String industry;
    private String city;
    private Integer employeeCount;
    private Integer foundedYear;

    @GraphQLTemporal
    private LocalDateTime createdAt;

    private Long sequence;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
