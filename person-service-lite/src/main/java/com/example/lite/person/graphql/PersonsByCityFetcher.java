package com.example.lite.person.graphql;

import com.example.lite.person.domain.Person;
import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

/** Legacy-style fetcher for {@code Query.personsByCity}. */
public class PersonsByCityFetcher implements DataFetcher<List<Person>> {

    private final PersonService personService;

    public PersonsByCityFetcher(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public List<Person> get(DataFetchingEnvironment environment) {
        return personService.getPersonsByCity(environment.getArgument("city"));
    }
}
