package com.example.person.graphql.mutation;

import com.example.infrastructure.graphql.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import com.example.person.service.dto.CreatePersonInput;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Mutation.createPerson}. */
@Component
public class CreatePersonResolver implements GraphQLResolver {

    private final PersonService personService;
    private final GraphQLArgumentMapper argumentMapper;

    public CreatePersonResolver(PersonService personService, GraphQLArgumentMapper argumentMapper) {
        this.personService = personService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "createPerson";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        CreatePersonInput input = argumentMapper.argument(environment, "input", CreatePersonInput.class);
        return personService.createPerson(input);
    }
}
