package com.example.infrastructure.filter;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts raw filter argument values (as GraphQL delivers them: String, Integer,
 * Double, LocalDate...) into the Java type of the entity attribute being filtered -
 * e.g. a {@code Double} from a Float argument into the entity's {@code BigDecimal},
 * or a String into the entity's enum constant.
 */
@Component
public class FilterValueCoercer {

    private final ObjectMapper objectMapper;

    public FilterValueCoercer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object coerce(Object rawValue, Class<?> targetType) {
        try {
            return objectMapper.convertValue(rawValue, targetType);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "Filter value '" + rawValue + "' cannot be converted to " + targetType.getSimpleName(),
                    Collections.singletonList(new ErrorDetail(null, "type-coercion", e.getMessage())));
        }
    }

    public List<Object> coerceAll(List<?> rawValues, Class<?> targetType) {
        return rawValues.stream().map(value -> coerce(value, targetType)).collect(Collectors.toList());
    }
}
