package com.example.infrastructure.enums;

/**
 * One enriched enum value as exposed by the GraphQL {@code EnumValue} type: the stored
 * code plus human-readable label and optional description.
 */
public final class EnumEntry {

    private final String code;
    private final String label;
    private final String description;

    public EnumEntry(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
