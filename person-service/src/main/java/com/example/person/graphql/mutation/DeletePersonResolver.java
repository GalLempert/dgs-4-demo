package com.example.person.graphql.mutation;

import com.example.infrastructure.graphql.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Mutation.deletePerson}. */
@Component
public class DeletePersonResolver implements GraphQLResolver {

    private final PersonService personService;
    private final GraphQLArgumentMapper argumentMapper;

    public DeletePersonResolver(PersonService personService, GraphQLArgumentMapper argumentMapper) {
        this.personService = personService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "deletePerson";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return personService.deletePerson(argumentMapper.longArgument(environment, "id"));
    }
}
