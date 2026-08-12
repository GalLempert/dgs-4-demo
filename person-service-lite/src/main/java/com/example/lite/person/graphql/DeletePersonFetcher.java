package com.example.lite.person.graphql;

import com.example.lite.person.service.PersonService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/** Legacy-style fetcher for {@code Mutation.deletePerson}. */
public class DeletePersonFetcher implements DataFetcher<Boolean> {

    private final PersonService personService;

    public DeletePersonFetcher(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public Boolean get(DataFetchingEnvironment environment) {
        long id = Long.parseLong(environment.getArgument("id"));
        return personService.deletePerson(id);
    }
}
