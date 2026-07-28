package com.example.person.graphql.query;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterParser;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Handles {@code Query.countPersonsByFilter(filter, includeDeleted)}: counts the
 * persons a filter matches without fetching them. Soft-deleted rows are excluded
 * unless {@code includeDeleted} is true.
 */
@Component
public class CountPersonsByFilterResolver implements GraphQLResolver {

    private final PersonService personService;
    private final FilterParser filterParser;

    public CountPersonsByFilterResolver(PersonService personService, FilterParser filterParser) {
        this.personService = personService;
        this.filterParser = filterParser;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "countPersonsByFilter";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        FilterCriteria criteria = filterParser.parse(environment.getArgument("filter"));
        boolean includeDeleted = Boolean.TRUE.equals(environment.getArgument("includeDeleted"));
        return personService.countPersons(criteria, includeDeleted);
    }
}
