package com.example.person.graphql.mutation;

import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.person.service.PersonService;
import com.example.person.service.dto.CreatePersonInput;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/** Handles {@code Mutation.createPerson}. */
@Component
public class CreatePersonResolver implements GraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(CreatePersonResolver.class);

    private final PersonService personService;
    private final GraphQLArgumentMapper argumentMapper;

    public CreatePersonResolver(PersonService personService, GraphQLArgumentMapper argumentMapper) {
        this.personService = personService;
        this.argumentMapper = argumentMapper;
    }

    @Override
    public GraphQLOperationType operationType() {
        return GraphQLOperationType.MUTATION;
    }

    @Override
    public String fieldName() {
        return "createPerson";
    }

    /** The raw {@code input} argument is validated against json-schema/person-create.json. */
    @Override
    public Map<String, String> argumentJsonSchemas() {
        return Collections.singletonMap("input", "person-create");
    }

    @Override
    public Object resolve(DataFetchingEnvironment environment) {
        CreatePersonInput input = argumentMapper.argument(environment, "input", CreatePersonInput.class);
        log.debug("createPerson input mapped for email={}", input.getEmail());
        return personService.createPerson(input);
    }
}
