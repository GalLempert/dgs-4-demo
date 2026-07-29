package com.example.company.graphql;

import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.company.service.CompanyService;
import com.example.company.service.dto.CreateCompanyInput;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Mutation.createCompany}. */
@Component
public class CreateCompanyResolver implements GraphQLResolver {

    private final CompanyService companyService;
    private final GraphQLArgumentMapper argumentMapper;

    public CreateCompanyResolver(CompanyService companyService, GraphQLArgumentMapper argumentMapper) {
        this.companyService = companyService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "createCompany";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        CreateCompanyInput input = argumentMapper.argument(environment, "input", CreateCompanyInput.class);
        return companyService.createCompany(input);
    }
}
