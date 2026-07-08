package com.example.person.graphql.query;

import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Query.personById}. */
@Component
public class PersonByIdResolver implements GraphQLResolver {

    private final PersonService personService;

    public PersonByIdResolver(PersonService personService) {
        this.personService = personService;
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
        long id = Long.parseLong(environment.getArgument("id"));
        return personService.getPerson(id);
    }
}
