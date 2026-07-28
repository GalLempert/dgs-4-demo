package com.example.company.config;

import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.infrastructure.graphql.model.GraphQLModelSource;
import com.example.infrastructure.replication.ReplicationResolverFactory;
import com.example.company.service.CompanyService;
import com.example.company.service.dto.CompanyView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * The complete GraphQL query wiring of the company domain: the model registration and
 * one bean per standard replicated-resource query, all manufactured by the
 * infrastructure's {@link ReplicationResolverFactory}. Together with the schema file
 * this is everything the four queries need - the domain writes no resolver code.
 */
@Configuration
public class CompanyGraphQLConfig {

    @Bean
    public GraphQLModelSource companyGraphQLModels() {
        return () -> Collections.singletonList(CompanyView.class);
    }

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
}
