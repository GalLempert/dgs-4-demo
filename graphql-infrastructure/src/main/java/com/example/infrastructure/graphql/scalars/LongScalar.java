package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;

/**
 * 64-bit signed integer scalar. GraphQL's built-in {@code Int} is 32-bit by spec;
 * replication sequence numbers are unbounded counters, so they travel as {@code Long}
 * (serialized as a JSON number, accepted as a number or a numeric string).
 *
 * <p>Coercion is exact or rejected: a fractional number ({@code 1.9}) or a value
 * outside the signed 64-bit range is an input error, never a silent truncation -
 * for replication cursors a silently altered value would move the client to an
 * unintended feed position.
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
            try {
                return ((IntValue) input).getValue().longValueExact();
            } catch (ArithmeticException e) {
                throw new CoercingParseLiteralException(
                        "'" + ((IntValue) input).getValue() + "' is outside the 64-bit Long range");
            }
        }
        if (input instanceof StringValue) {
            return toLong(((StringValue) input).getValue(), CoercingParseLiteralException::new);
        }
        throw new CoercingParseLiteralException("Expected an Int or String literal for Long");
    }

    /** Exact conversion only: fractional or out-of-range values are rejected, never truncated. */
    private <E extends RuntimeException> Long toLong(Object value, Function<String, E> errorFactory) {
        try {
            if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
                return ((Number) value).longValue();
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).longValueExact();
            }
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).longValueExact();
            }
            if (value instanceof Double || value instanceof Float) {
                return new BigDecimal(value.toString()).longValueExact();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (ArithmeticException | NumberFormatException e) {
            throw errorFactory.apply("'" + value + "' is not a valid Long (must be a whole number within 64-bit range)");
        }
    }
}
