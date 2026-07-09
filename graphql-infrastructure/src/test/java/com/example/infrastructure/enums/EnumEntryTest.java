package com.example.infrastructure.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumEntryTest {

    @Test
    void exposesAllFields() {
        EnumEntry entry = new EnumEntry("MALE", "Male", "desc");

        assertThat(entry.getCode()).isEqualTo("MALE");
        assertThat(entry.getLabel()).isEqualTo("Male");
        assertThat(entry.getDescription()).isEqualTo("desc");
    }

    @Test
    void descriptionIsOptional() {
        assertThat(new EnumEntry("X", "X", null).getDescription()).isNull();
    }
}
