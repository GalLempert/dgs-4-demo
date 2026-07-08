package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ISO-8601 date-time scalar ({@code "2026-07-08T14:30:00"}) mapped to {@link LocalDateTime}.
 */
@DgsScalar(name = "DateTime")
public class LocalDateTimeScalar implements Coercing<LocalDateTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return FORMAT.format((LocalDateTime) dataFetcherResult);
        }
        throw new CoercingSerializeException(
                "Expected a LocalDateTime but got " + dataFetcherResult.getClass().getSimpleName());
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        try {
            return LocalDateTime.parse(input.toString(), FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoercingParseValueException("Not a valid ISO date-time: " + input, e);
        }
    }

    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            try {
                return LocalDateTime.parse(((StringValue) input).getValue(), FORMAT);
            } catch (DateTimeParseException e) {
                throw new CoercingParseLiteralException("Not a valid ISO date-time literal: " + input, e);
            }
        }
        throw new CoercingParseLiteralException("Expected a String literal for DateTime");
    }
}
