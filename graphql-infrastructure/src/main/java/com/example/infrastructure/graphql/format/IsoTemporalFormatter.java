package com.example.infrastructure.graphql.format;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/** ISO-8601, the default: {@code 2026-07-08} / {@code 2026-07-08T14:30:00}. */
@Component
public class IsoTemporalFormatter implements TemporalFormatter {

    @Override
    public String formatName() {
        return "ISO";
    }

    @Override
    public String format(TemporalAccessor value) {
        return Temporals.hasTimeComponent(value)
                ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value)
                : DateTimeFormatter.ISO_LOCAL_DATE.format(value);
    }
}
