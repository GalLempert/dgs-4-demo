package com.example.person.graphql.query;

import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Query.allPersons}. */
@Component
public class AllPersonsResolver implements GraphQLResolver {

    private final PersonService personService;

    public AllPersonsResolver(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "allPersons";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return personService.getAllPersons();
    }
}
