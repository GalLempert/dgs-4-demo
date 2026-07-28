package com.example.person.graphql.query;

import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Handles {@code Query.personMaxSequence}: the highest replication sequence currently
 * in the person table, so a client that wants to skip the initial import can start
 * polling {@code personsBySequence} from the live tail.
 */
@Component
public class PersonMaxSequenceResolver implements GraphQLResolver {

    private final PersonService personService;

    public PersonMaxSequenceResolver(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "personMaxSequence";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        return personService.getMaxSequence();
    }
}
