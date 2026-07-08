package com.example.person.graphql.mutation;

import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Handles {@code Mutation.updatePersonSalary}. */
@Component
public class UpdatePersonSalaryResolver implements GraphQLResolver {

    private final PersonService personService;

    public UpdatePersonSalaryResolver(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "updatePersonSalary";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        long id = Long.parseLong(environment.getArgument("id"));
        Number salary = environment.getArgument("salary");
        return personService.updateSalary(id, BigDecimal.valueOf(salary.doubleValue()));
    }
}
