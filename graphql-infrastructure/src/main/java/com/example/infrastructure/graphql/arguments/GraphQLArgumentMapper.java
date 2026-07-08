package com.example.infrastructure.graphql.arguments;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;

/**
 * Converts raw GraphQL arguments (maps/lists/scalars as produced by graphql-java) into
 * typed values and DTOs, so resolvers never hand-parse input. Malformed arguments
 * become INVALID_ARGUMENT (400) errors instead of leaking parse exceptions.
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

    /** Parses an {@code ID} argument as a numeric id; rejects with 400 instead of a 500. */
    public long longArgument(DataFetchingEnvironment environment, String argumentName) {
        Object raw = environment.getArgument(argumentName);
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw invalidArgument(argumentName, "numeric",
                    "Argument '" + argumentName + "' must be a whole number but was '" + raw + "'");
        }
    }

    public BigDecimal decimalArgument(DataFetchingEnvironment environment, String argumentName) {
        Number raw = environment.getArgument(argumentName);
        if (raw == null) {
            throw invalidArgument(argumentName, "required", "Argument '" + argumentName + "' is required");
        }
        return BigDecimal.valueOf(raw.doubleValue());
    }

    private ApiException invalidArgument(String argumentName, String constraint, String reason) {
        return new ApiException(ErrorCode.INVALID_ARGUMENT, reason,
                Collections.singletonList(new ErrorDetail(argumentName, constraint, reason)));
    }
}
