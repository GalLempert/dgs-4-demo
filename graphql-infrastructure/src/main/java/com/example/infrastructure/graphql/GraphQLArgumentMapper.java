package com.example.infrastructure.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Converts raw GraphQL arguments (maps/lists/scalars as produced by graphql-java) into
 * typed DTOs, so resolvers don't have to hand-parse nested input objects.
 */
@Component
public class GraphQLArgumentMapper {

    private final ObjectMapper objectMapper;

    public GraphQLArgumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T argument(DataFetchingEnvironment environment, String argumentName, Class<T> targetType) {
        Object raw = environment.getArgument(argumentName);
        return objectMapper.convertValue(raw, targetType);
    }
}
