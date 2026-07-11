package com.example.person.graphql.query;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterParser;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Handles {@code Query.persons(filter)}: the raw filter argument is parsed into a
 * framework-neutral {@link FilterCriteria}, so the service and DAL never see GraphQL
 * structures.
 */
@Component
public class PersonsResolver implements GraphQLResolver {

    private final PersonService personService;
    private final FilterParser filterParser;

    public PersonsResolver(PersonService personService, FilterParser filterParser) {
        this.personService = personService;
        this.filterParser = filterParser;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.QUERY;
    }

    @Override
    public String fieldName() {
        return "persons";
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        FilterCriteria criteria = filterParser.parse(environment.getArgument("filter"));
        return personService.findPersons(criteria);
    }
}
