package com.example.infrastructure.filter;

import java.util.Collections;
import java.util.Map;

/**
 * One parsed filter leaf: a field path on the entity (dots for nesting, e.g.
 * {@code address.city}), the predicate to apply ({@code equals}, {@code like},
 * {@code between}...) and the predicate's arguments ({@code value}, or
 * {@code from}/{@code to}, or {@code values} - depending on the predicate).
 */
public final class FieldFilter {

    private final String fieldPath;
    private final String predicate;
    private final Map<String, Object> arguments;

    public FieldFilter(String fieldPath, String predicate, Map<String, Object> arguments) {
        this.fieldPath = fieldPath;
        this.predicate = predicate;
        this.arguments = Collections.unmodifiableMap(arguments);
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public String getPredicate() {
        return predicate;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return fieldPath + " " + predicate + " " + arguments;
    }
}
