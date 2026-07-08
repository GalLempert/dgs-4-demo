package com.example.person.graphql.query;

import com.example.infrastructure.graphql.GraphQLOperationType;
import com.example.infrastructure.graphql.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/** Handles {@code Query.personsByCity}. */
@Component
public class PersonsByCityResolver implements GraphQLResolver {

    private final PersonService personService;

    public PersonsByCityResolver(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "personsByCity";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        String city = environment.getArgument("city");
        return personService.getPersonsByCity(city);
    }
}
