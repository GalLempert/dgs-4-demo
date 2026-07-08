package com.example.infrastructure.graphql;

import com.example.infrastructure.exception.ApiException;
import com.example.infrastructure.exception.ErrorCode;
import com.example.infrastructure.exception.ErrorDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Converts raw GraphQL arguments (maps/lists/scalars as produced by graphql-java) into
 * typed DTOs, so resolvers don't have to hand-parse nested input objects.
 */
@Component
public class GraphQLArgumentMapper {

    private static final Logger log = LoggerFactory.getLogger(GraphQLArgumentMapper.class);

    private final ObjectMapper objectMapper;

    public GraphQLArgumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T argument(DataFetchingEnvironment environment, String argumentName, Class<T> targetType) {
        Object raw = environment.getArgument(argumentName);
        try {
            T converted = objectMapper.convertValue(raw, targetType);
            log.debug("Converted argument '{}' to {}", argumentName, targetType.getSimpleName());
            return converted;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "Argument '" + argumentName + "' could not be mapped to " + targetType.getSimpleName(),
                    Collections.singletonList(new ErrorDetail(argumentName, "type-mapping", e.getMessage())));
        }
    }
}
