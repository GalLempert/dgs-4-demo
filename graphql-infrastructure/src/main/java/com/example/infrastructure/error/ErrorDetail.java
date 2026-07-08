package com.example.infrastructure.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One machine-readable reason why a request failed: which field, which constraint was
 * broken, and a human-readable explanation. Rendered as an entry of the
 * {@code extensions.details} array on the GraphQL error.
 */
public final class ErrorDetail {

    private final String field;
    private final String constraint;
    private final String reason;

    public ErrorDetail(String field, String constraint, String reason) {
        this.field = field;
        this.constraint = constraint;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getConstraint() {
        return constraint;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (field != null) {
            map.put("field", field);
        }
        if (constraint != null) {
            map.put("constraint", constraint);
        }
        if (reason != null) {
            map.put("reason", reason);
        }
        return map;
    }

    @Override
    public String toString() {
        return toMap().toString();
    }
}
