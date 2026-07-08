package com.example.infrastructure.graphql.format;

import java.time.temporal.TemporalAccessor;

/**
 * Strategy for rendering a temporal value in one named output format. The name must
 * match a literal of the schema's {@code DateFormat} enum; adding a format is adding
 * one bean.
 */
public interface TemporalFormatter {

    /** The format literal clients pass, e.g. {@code "UNIX"}. */
    String formatName();

    String format(TemporalAccessor value);
}
