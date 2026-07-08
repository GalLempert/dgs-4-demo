package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ISO-8601 date scalar ({@code "2026-07-08"}) mapped to {@link LocalDate}.
 */
@DgsScalar(name = "Date")
public class LocalDateScalar implements Coercing<LocalDate, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDate) {
            return FORMAT.format((LocalDate) dataFetcherResult);
        }
        throw new CoercingSerializeException(
                "Expected a LocalDate but got " + dataFetcherResult.getClass().getSimpleName());
    }

    @Override
    public LocalDate parseValue(Object input) throws CoercingParseValueException {
        try {
            return LocalDate.parse(input.toString(), FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoercingParseValueException("Not a valid ISO date: " + input, e);
        }
    }

    @Override
    public LocalDate parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            try {
                return LocalDate.parse(((StringValue) input).getValue(), FORMAT);
            } catch (DateTimeParseException e) {
                throw new CoercingParseLiteralException("Not a valid ISO date literal: " + input, e);
            }
        }
        throw new CoercingParseLiteralException("Expected a String literal for Date");
    }
}
