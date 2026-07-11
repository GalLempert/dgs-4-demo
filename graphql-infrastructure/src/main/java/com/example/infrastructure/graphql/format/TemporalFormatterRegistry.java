package com.example.infrastructure.graphql.format;

import com.example.infrastructure.support.UniqueIndex;
import org.springframework.stereotype.Component;

import java.time.temporal.TemporalAccessor;
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
        this.formattersByName = UniqueIndex.byKey(formatters,
                TemporalFormatter::formatName, "temporal formatters");
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
