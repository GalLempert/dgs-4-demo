package com.example.infrastructure.graphql.format;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalFormattersTest {

    private final IsoTemporalFormatter iso = new IsoTemporalFormatter();
    private final UnixTemporalFormatter unix = new UnixTemporalFormatter();
    private final Rfc1123TemporalFormatter rfc = new Rfc1123TemporalFormatter();

    @Test
    void isoFormatsDateWithoutTimeComponent() {
        assertThat(iso.format(LocalDate.of(1985, 12, 10))).isEqualTo("1985-12-10");
    }

    @Test
    void isoFormatsDateTimeWithTimeComponent() {
        assertThat(iso.format(LocalDateTime.of(1985, 12, 10, 8, 45, 30)))
                .isEqualTo("1985-12-10T08:45:30");
    }

    @Test
    void unixTreatsBareDatesAsUtcMidnight() {
        assertThat(unix.format(LocalDate.of(1985, 12, 10))).isEqualTo("503020800");
        assertThat(unix.format(LocalDate.of(1970, 1, 1))).isEqualTo("0");
    }

    @Test
    void unixFormatsDateTimes() {
        assertThat(unix.format(LocalDateTime.of(1970, 1, 1, 0, 0, 10))).isEqualTo("10");
    }

    @Test
    void rfc1123FormatsInUtc() {
        assertThat(rfc.format(LocalDate.of(1985, 12, 10)))
                .isEqualTo("Tue, 10 Dec 1985 00:00:00 GMT");
    }

    @Test
    void formatNamesMatchSchemaEnumLiterals() {
        assertThat(iso.formatName()).isEqualTo("ISO");
        assertThat(unix.formatName()).isEqualTo("UNIX");
        assertThat(rfc.formatName()).isEqualTo("RFC_1123");
    }

    @Test
    void registryDispatchesByName() {
        TemporalFormatterRegistry registry = new TemporalFormatterRegistry(Arrays.asList(iso, unix, rfc));
        assertThat(registry.format("UNIX", LocalDate.of(1970, 1, 1))).isEqualTo("0");
        assertThat(registry.format("ISO", LocalDate.of(1970, 1, 1))).isEqualTo("1970-01-01");
    }

    @Test
    void registryRejectsUnknownFormatWithAvailableNames() {
        TemporalFormatterRegistry registry = new TemporalFormatterRegistry(Arrays.asList(iso, unix));
        assertThatThrownBy(() -> registry.format("STARDATE", LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STARDATE")
                .hasMessageContaining("ISO");
    }

    @Test
    void registryFailsFastOnDuplicateFormatNames() {
        assertThatThrownBy(() -> new TemporalFormatterRegistry(
                Arrays.asList(new IsoTemporalFormatter(), new IsoTemporalFormatter())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }
}
