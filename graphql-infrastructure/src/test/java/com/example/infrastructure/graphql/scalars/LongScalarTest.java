package com.example.infrastructure.graphql.scalars;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongScalarTest {

    private final LongScalar scalar = new LongScalar();

    @Test
    void acceptsWholeNumbersInAnyNumericShape() {
        assertThat(scalar.parseValue(42)).isEqualTo(42L);
        assertThat(scalar.parseValue(42L)).isEqualTo(42L);
        assertThat(scalar.parseValue("42")).isEqualTo(42L);
        assertThat(scalar.parseValue(new BigDecimal("42"))).isEqualTo(42L);
        assertThat(scalar.parseValue(BigInteger.valueOf(42))).isEqualTo(42L);
        assertThat(scalar.parseValue(42.0d)).isEqualTo(42L);
        assertThat(scalar.parseValue(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsFractionalNumbersInsteadOfTruncating() {
        // 1.9 must NOT silently become 1 - a replication cursor would move to an
        // unintended position
        assertThatThrownBy(() -> scalar.parseValue(1.9d)).isInstanceOf(CoercingParseValueException.class);
        assertThatThrownBy(() -> scalar.parseValue(new BigDecimal("1.5")))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void rejectsValuesOutsideTheSigned64BitRange() {
        BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> scalar.parseValue(tooBig)).isInstanceOf(CoercingParseValueException.class);
        assertThatThrownBy(() -> scalar.parseValue("not-a-number"))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void parsesLiteralsExactly() {
        assertThat(scalar.parseLiteral(new IntValue(BigInteger.valueOf(7)))).isEqualTo(7L);
        assertThat(scalar.parseLiteral(new StringValue("12"))).isEqualTo(12L);

        BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> scalar.parseLiteral(new IntValue(tooBig)))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void serializesNumbersExactly() {
        assertThat(scalar.serialize(42L)).isEqualTo(42L);
        assertThat(scalar.serialize(42)).isEqualTo(42L);
    }
}
