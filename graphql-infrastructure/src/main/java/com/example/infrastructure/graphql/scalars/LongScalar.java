package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

/**
 * 64-bit signed integer scalar. GraphQL's built-in {@code Int} is 32-bit by spec;
 * replication sequence numbers are unbounded counters, so they travel as {@code Long}
 * (serialized as a JSON number, accepted as a number or a numeric string).
 */
@DgsScalar(name = "Long")
public class LongScalar implements Coercing<Long, Long> {

    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        return toLong(dataFetcherResult, CoercingSerializeException::new);
    }

    @Override
    public Long parseValue(Object input) throws CoercingParseValueException {
        return toLong(input, CoercingParseValueException::new);
    }

    @Override
    public Long parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().longValueExact();
        }
        if (input instanceof StringValue) {
            return toLong(((StringValue) input).getValue(), CoercingParseLiteralException::new);
        }
        throw new CoercingParseLiteralException("Expected an Int or String literal for Long");
    }

    private <E extends RuntimeException> Long toLong(Object value, java.util.function.Function<String, E> errorFactory) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw errorFactory.apply("'" + value + "' is not a valid Long");
        }
    }
}
