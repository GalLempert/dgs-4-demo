package com.example.lite.person.graphql;

import com.example.lite.person.domain.Person;
import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/**
 * Legacy-style fetcher for {@code Query.personById}. GraphQL {@code ID} arguments
 * arrive as strings; translating them is this layer's job, the service below sees a
 * plain {@code long} - the same boundary the old in-house framework drew.
 */
public class PersonByIdFetcher implements DataFetcher<Person> {

    private final PersonService personService;

    public PersonByIdFetcher(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public Person get(DataFetchingEnvironment environment) {
        long id = Long.parseLong(environment.getArgument("id"));
        return personService.getPerson(id);
    }
}
