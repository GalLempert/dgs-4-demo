package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ISO-8601 date-time scalar ({@code "2026-07-08T14:30:00"}) mapped to {@link LocalDateTime}.
 */
@DgsScalar(name = "DateTime")
public class LocalDateTimeScalar extends TemporalScalar<LocalDateTime> {

    public LocalDateTimeScalar() {
        super(LocalDateTime.class, DateTimeFormatter.ISO_LOCAL_DATE_TIME, LocalDateTime::from);
    }
}
