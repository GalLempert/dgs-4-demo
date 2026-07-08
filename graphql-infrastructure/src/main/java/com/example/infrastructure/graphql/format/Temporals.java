package com.example.infrastructure.graphql.format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

/**
 * Shared temporal coercion for formatters: normalizes any date-bearing value to a
 * {@link LocalDateTime} (dates without a time component are taken at midnight).
 */
final class Temporals {

    private Temporals() {
    }

    static LocalDateTime toLocalDateTime(TemporalAccessor value) {
        LocalDate date = value.query(TemporalQueries.localDate());
        if (date == null) {
            throw new IllegalArgumentException("Value has no date component: " + value);
        }
        LocalTime time = value.query(TemporalQueries.localTime());
        return LocalDateTime.of(date, time != null ? time : LocalTime.MIDNIGHT);
    }

    static boolean hasTimeComponent(TemporalAccessor value) {
        return value.query(TemporalQueries.localTime()) != null;
    }
}
