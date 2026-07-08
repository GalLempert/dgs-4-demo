package com.example.infrastructure.graphql.scalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Template for ISO-8601 temporal scalars. Concrete scalars only declare which Java
 * type, formatter and temporal query they use - serialization, value parsing and
 * literal parsing live here once.
 */
public abstract class TemporalScalar<T extends TemporalAccessor> implements Coercing<T, String> {

    private final Class<T> javaType;
    private final DateTimeFormatter formatter;
    private final TemporalQuery<T> query;

    protected TemporalScalar(Class<T> javaType, DateTimeFormatter formatter, TemporalQuery<T> query) {
        this.javaType = javaType;
        this.formatter = formatter;
        this.query = query;
    }

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (!javaType.isInstance(dataFetcherResult)) {
            throw new CoercingSerializeException("Expected a " + javaType.getSimpleName()
                    + " but got " + dataFetcherResult.getClass().getSimpleName());
        }
        return formatter.format(javaType.cast(dataFetcherResult));
    }

    @Override
    public T parseValue(Object input) throws CoercingParseValueException {
        return parse(String.valueOf(input), CoercingParseValueException::new);
    }

    @Override
    public T parseLiteral(Object input) throws CoercingParseLiteralException {
        return Optional.ofNullable(input)
                .filter(StringValue.class::isInstance)
                .map(StringValue.class::cast)
                .map(StringValue::getValue)
                .map(text -> parse(text, CoercingParseLiteralException::new))
                .orElseThrow(() -> new CoercingParseLiteralException(
                        "Expected a String literal for " + javaType.getSimpleName()));
    }

    private <E extends RuntimeException> T parse(String text, BiFunction<String, Throwable, E> errorFactory) {
        try {
            return formatter.parse(text, query);
        } catch (DateTimeParseException e) {
            throw errorFactory.apply("'" + text + "' is not a valid ISO " + javaType.getSimpleName(), e);
        }
    }
}
