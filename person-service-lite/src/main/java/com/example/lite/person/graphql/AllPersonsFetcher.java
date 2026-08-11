package com.example.lite.person.graphql;

import com.example.lite.person.domain.Person;
import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

/**
 * Legacy-style fetcher for {@code Query.allPersons}: a plain
 * {@link DataFetcher} exactly as graphql-java-annotations expects them - no Spring,
 * no framework imports. In a real migration this class already exists and is reused
 * unchanged; only the one-line wiring in
 * {@link com.example.lite.person.config.PersonLiteGraphQLConfig} is new.
 */
public class AllPersonsFetcher implements DataFetcher<List<Person>> {

    private final PersonService personService;

    public AllPersonsFetcher(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public List<Person> get(DataFetchingEnvironment environment) {
        return personService.getAllPersons();
    }
}
