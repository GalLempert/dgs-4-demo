package com.example.infrastructure.graphql.scalars;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalScalarsTest {

    private final LocalDateScalar dateScalar = new LocalDateScalar();
    private final LocalDateTimeScalar dateTimeScalar = new LocalDateTimeScalar();

    // ---------------------------------------------------------------- Date

    @Test
    void serializesLocalDateAsIso() {
        assertThat(dateScalar.serialize(LocalDate.of(2026, 7, 8))).isEqualTo("2026-07-08");
    }

    @Test
    void serializeRejectsForeignTypes() {
        assertThatThrownBy(() -> dateScalar.serialize("2026-07-08"))
                .isInstanceOf(CoercingSerializeException.class)
                .hasMessageContaining("LocalDate");
    }

    @Test
    void parsesValueFromIsoString() {
        assertThat(dateScalar.parseValue("2026-07-08")).isEqualTo(LocalDate.of(2026, 7, 8));
    }

    @Test
    void parseValueRejectsGarbage() {
        assertThatThrownBy(() -> dateScalar.parseValue("not-a-date"))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void parsesLiteralFromStringValue() {
        assertThat(dateScalar.parseLiteral(new StringValue("2026-07-08")))
                .isEqualTo(LocalDate.of(2026, 7, 8));
    }

    @Test
    void parseLiteralRejectsNonStringLiterals() {
        assertThatThrownBy(() -> dateScalar.parseLiteral(new IntValue(BigInteger.TEN)))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseLiteralRejectsMalformedDates() {
        assertThatThrownBy(() -> dateScalar.parseLiteral(new StringValue("2026-13-45")))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    // ------------------------------------------------------------ DateTime

    @Test
    void serializesLocalDateTimeAsIso() {
        assertThat(dateTimeScalar.serialize(LocalDateTime.of(2026, 7, 8, 14, 30)))
                .isEqualTo("2026-07-08T14:30:00");
    }

    @Test
    void dateTimeSerializeRejectsLocalDate() {
        assertThatThrownBy(() -> dateTimeScalar.serialize(LocalDate.of(2026, 7, 8)))
                .isInstanceOf(CoercingSerializeException.class);
    }

    @Test
    void dateTimeRoundTripsThroughParseValue() {
        LocalDateTime value = LocalDateTime.of(2026, 7, 8, 14, 30, 15);
        assertThat(dateTimeScalar.parseValue(dateTimeScalar.serialize(value))).isEqualTo(value);
    }
}
