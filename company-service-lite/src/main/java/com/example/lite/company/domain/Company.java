package com.example.lite.company.domain;

/** The company domain object - a plain POJO, GraphQL fields read off the getters. */
public class Company {

    private Long id;
    private String name;
    private String industry;

    public Company() {
    }

    public Company(Long id, String name, String industry) {
        this.id = id;
        this.name = name;
        this.industry = industry;
    }

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
}
