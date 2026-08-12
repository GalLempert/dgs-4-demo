package com.example.lite.person.graphql;

import com.example.lite.graphql.GraphQLResolver;
import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Handles {@code Query.personCount} - the one resolver written natively against the
 * lite contract rather than wrapping a legacy fetcher, showing the style new
 * operations take after the migration: a {@code @Component} the dispatch controller
 * picks up by itself, no config-class wiring needed.
 */
@Component
public class PersonCountResolver implements GraphQLResolver {

    private final PersonService personService;

    public PersonCountResolver(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public String parentType() {
        return "Query";
    }

    @Override
    public String fieldName() {
        return "personCount";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return personService.countPersons();
    }
}
