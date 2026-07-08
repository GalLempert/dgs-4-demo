package com.example.infrastructure.graphql.format;

import org.springframework.stereotype.Component;

import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes the {@link TemporalFormatter} beans by format name. Fails fast at startup on
 * duplicate names.
 */
@Component
public class TemporalFormatterRegistry {

    private final Map<String, TemporalFormatter> formattersByName;

    public TemporalFormatterRegistry(List<TemporalFormatter> formatters) {
        Map<String, TemporalFormatter> index = new LinkedHashMap<>();
        for (TemporalFormatter formatter : formatters) {
            TemporalFormatter previous = index.put(formatter.formatName(), formatter);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "Duplicate temporal formatters for '%s': %s and %s",
                        formatter.formatName(),
                        previous.getClass().getName(),
                        formatter.getClass().getName()));
            }
        }
        this.formattersByName = Collections.unmodifiableMap(index);
    }

    public String format(String formatName, TemporalAccessor value) {
        TemporalFormatter formatter = formattersByName.get(formatName);
        if (formatter == null) {
            throw new IllegalStateException("No temporal formatter named '" + formatName
                    + "' (available: " + formattersByName.keySet() + ")");
        }
        return formatter.format(value);
    }
}
