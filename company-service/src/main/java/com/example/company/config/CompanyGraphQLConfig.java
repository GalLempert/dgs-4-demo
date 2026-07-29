package com.example.company.config;

import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.infrastructure.graphql.model.GraphQLModelSource;
import com.example.infrastructure.replication.MutationResolverFactory;
import com.example.infrastructure.replication.ReplicationResolverFactory;
import com.example.company.service.CompanyService;
import com.example.company.service.dto.CompanyView;
import com.example.company.service.dto.CreateCompanyInput;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * The complete GraphQL wiring of the company domain: the model registration plus one
 * bean per standard replicated-resource query AND mutation, all manufactured by the
 * infrastructure's {@link ReplicationResolverFactory} / {@link MutationResolverFactory}.
 * Together with the schema file this is everything the operations need - the domain
 * writes no resolver code at all.
 */
@Configuration
public class CompanyGraphQLConfig {

    @Bean
    public GraphQLModelSource companyGraphQLModels() {
        return () -> Collections.singletonList(CompanyView.class);
    }

    // -------------------------------------------------------- standard queries

    @Bean
    public GraphQLResolver companies(ReplicationResolverFactory factory, CompanyService service) {
        return factory.filteredList("companies", service);
    }

    @Bean
    public GraphQLResolver companiesBySequence(ReplicationResolverFactory factory, CompanyService service) {
        return factory.bySequence("companiesBySequence", service);
    }

    @Bean
    public GraphQLResolver countCompaniesByFilter(ReplicationResolverFactory factory, CompanyService service) {
        return factory.countByFilter("countCompaniesByFilter", service);
    }

    @Bean
    public GraphQLResolver companyMaxSequence(ReplicationResolverFactory factory, CompanyService service) {
        return factory.maxSequence("companyMaxSequence", service);
    }

    // ------------------------------------------------------ standard mutations
    // The UpdateCompanyInput schema type maps onto the same Java DTO as creation -
    // at merge time every field is optional anyway, only non-null fields apply.

    @Bean
    public GraphQLResolver createCompany(MutationResolverFactory factory, CompanyService service) {
        return factory.saveNew("createCompany", service, CreateCompanyInput.class);
    }

    @Bean
    public GraphQLResolver updateCompanies(MutationResolverFactory factory, CompanyService service) {
        return factory.updateByFilter("updateCompanies", service, CreateCompanyInput.class);
    }

    @Bean
    public GraphQLResolver saveOrUpdateCompany(MutationResolverFactory factory, CompanyService service) {
        return factory.saveOrUpdate("saveOrUpdateCompany", service, CreateCompanyInput.class, CreateCompanyInput.class);
    }

    @Bean
    public GraphQLResolver saveOrOverrideCompany(MutationResolverFactory factory, CompanyService service) {
        return factory.saveOrOverride("saveOrOverrideCompany", service, CreateCompanyInput.class);
    }

    @Bean
    public GraphQLResolver deleteCompanies(MutationResolverFactory factory, CompanyService service) {
        return factory.deleteByFilter("deleteCompanies", service);
    }

    @Bean
    public GraphQLResolver deleteCompany(MutationResolverFactory factory, CompanyService service) {
        return factory.deleteById("deleteCompany", service);
    }
}
