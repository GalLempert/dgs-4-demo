package com.example.lite.person.graphql;

import com.example.lite.person.domain.Person;
import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Map;

/**
 * Legacy-style fetcher for {@code Mutation.createPerson}. Input objects arrive as
 * maps; unpacking them here keeps the service layer GraphQL-free.
 */
public class CreatePersonFetcher implements DataFetcher<Person> {

    private final PersonService personService;

    public CreatePersonFetcher(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public Person get(DataFetchingEnvironment environment) {
        Map<String, Object> input = environment.getArgument("input");
        return personService.createPerson(
                (String) input.get("firstName"),
                (String) input.get("lastName"),
                (String) input.get("email"),
                (String) input.get("city"));
    }
}
