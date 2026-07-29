package com.example.company.graphql;

import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.company.service.CompanyService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Mutation.deleteCompany} - the soft delete inherited from the infrastructure. */
@Component
public class DeleteCompanyResolver implements GraphQLResolver {

    private final CompanyService companyService;
    private final GraphQLArgumentMapper argumentMapper;

    public DeleteCompanyResolver(CompanyService companyService, GraphQLArgumentMapper argumentMapper) {
        this.companyService = companyService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "deleteCompany";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return companyService.softDelete(argumentMapper.longArgument(environment, "id"));
    }
}
