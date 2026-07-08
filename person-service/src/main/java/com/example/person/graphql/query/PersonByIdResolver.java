package com.example.person.graphql.query;

import com.example.infrastructure.graphql.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Query.personById}. */
@Component
public class PersonByIdResolver implements GraphQLResolver {

    private final PersonService personService;
    private final GraphQLArgumentMapper argumentMapper;

    public PersonByIdResolver(PersonService personService, GraphQLArgumentMapper argumentMapper) {
        this.personService = personService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "personById";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return personService.getPerson(argumentMapper.longArgument(environment, "id"));
    }
}
