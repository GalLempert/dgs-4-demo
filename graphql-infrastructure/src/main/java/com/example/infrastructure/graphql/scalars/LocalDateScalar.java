package com.example.infrastructure.graphql.scalars;

import com.netflix.graphql.dgs.DgsScalar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * ISO-8601 date scalar ({@code "2026-07-08"}) mapped to {@link LocalDate}.
 */
@DgsScalar(name = "Date")
public class LocalDateScalar extends TemporalScalar<LocalDate> {

    public LocalDateScalar() {
        super(LocalDate.class, DateTimeFormatter.ISO_LOCAL_DATE, LocalDate::from);
    }
}
