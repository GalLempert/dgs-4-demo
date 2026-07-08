package com.example.infrastructure.graphql.format;

import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/** RFC 1123, UTC: {@code Tue, 10 Dec 1985 00:00:00 GMT}. */
@Component
public class Rfc1123TemporalFormatter implements TemporalFormatter {

    @Override
    public String formatName() {
        return "RFC_1123";
    }

    @Override
    public String format(TemporalAccessor value) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .format(Temporals.toLocalDateTime(value).atOffset(ZoneOffset.UTC));
    }
}
