package com.example.infrastructure.graphql.format;

import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;

/** Seconds since the Unix epoch, UTC: {@code 503020800}. */
@Component
public class UnixTemporalFormatter implements TemporalFormatter {

    @Override
    public String formatName() {
        return "UNIX";
    }

    @Override
    public String format(TemporalAccessor value) {
        return String.valueOf(Temporals.toLocalDateTime(value).toEpochSecond(ZoneOffset.UTC));
    }
}
