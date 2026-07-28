package com.example.person.graphql.query;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterParser;
import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Handles {@code Query.personsBySequence(sequence, bulkSize, filter)}: the replication
 * feed. Returns the next {@code bulkSize} persons whose replication sequence is
 * strictly greater than {@code sequence}, split into updated / deleted / filtered-out,
 * plus the sequence to resume from.
 */
@Component
public class PersonsBySequenceResolver implements GraphQLResolver {

    private final PersonService personService;
    private final GraphQLArgumentMapper argumentMapper;
    private final FilterParser filterParser;

    public PersonsBySequenceResolver(PersonService personService,
                                     GraphQLArgumentMapper argumentMapper,
                                     FilterParser filterParser) {
        this.personService = personService;
        this.argumentMapper = argumentMapper;
        this.filterParser = filterParser;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "personsBySequence";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        long sequence = argumentMapper.longArgument(environment, "sequence");
        int bulkSize = argumentMapper.argument(environment, "bulkSize", Integer.class);
        FilterCriteria criteria = filterParser.parse(environment.getArgument("filter"));
        return personService.getPersonsBySequence(sequence, bulkSize, criteria);
    }
}
