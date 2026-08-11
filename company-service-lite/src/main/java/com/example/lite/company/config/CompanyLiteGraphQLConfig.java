package com.example.lite.company.config;

import com.example.lite.company.service.CompanyService;
import com.example.lite.graphql.GraphQLResolver;
import com.example.lite.graphql.GraphQLResolvers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The complete GraphQL wiring of the lite company service: one bean per operation,
 * each a lambda fetcher calling the existing service - no fetcher classes, no
 * resolver classes. Where person-service-lite shows the migration path (legacy
 * fetchers reused unchanged), this config shows the end state a service converges to.
 */
@Configuration
public class CompanyLiteGraphQLConfig {

    @Bean
    public GraphQLResolver allCompanies(CompanyService service) {
        return GraphQLResolvers.query("allCompanies", environment -> service.getAllCompanies());
    }

    @Bean
    public GraphQLResolver companyById(CompanyService service) {
        return GraphQLResolvers.query("companyById",
                environment -> service.getCompany(Long.parseLong(environment.getArgument("id"))));
    }

    @Bean
    public GraphQLResolver createCompany(CompanyService service) {
        return GraphQLResolvers.mutation("createCompany",
                environment -> service.createCompany(
                        environment.getArgument("name"),
                        environment.getArgument("industry")));
    }
}
