package com.example.infrastructure.validation;

import com.example.infrastructure.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Uses src/test/resources/json-schema/test-entity.json (score must be 1..10). */
class JsonSchemaValidationServiceTest {

    private final JsonSchemaValidationService service = new JsonSchemaValidationService(
            new ObjectMapper(), new PathMatchingResourcePatternResolver());

    private Map<String, Object> payload(String name, Object score) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        if (score != null) {
            payload.put("score", score);
        }
        return payload;
    }

    @Test
    void validPayloadPassesSilently() {
        assertThatCode(() -> service.validate("test-entity", payload("Ada", 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void optionalFieldsMayBeAbsent() {
        assertThatCode(() -> service.validate("test-entity", payload("Ada", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void outOfRangeValueYieldsDetailedViolation() {
        SchemaValidationException exception = catchThrowableOfType(
                () -> service.validate("test-entity", payload("Ada", 11)),
                SchemaValidationException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCHEMA_VALIDATION_FAILED);
        assertThat(exception.getSchemaName()).isEqualTo("test-entity");
        assertThat(exception.getDetails()).hasSize(1);
        assertThat(exception.getDetails().get(0).getField()).contains("score");
        assertThat(exception.getDetails().get(0).getConstraint()).isEqualTo("maximum");
        assertThat(exception.getDetails().get(0).getReason()).contains("10");
    }

    @Test
    void multipleViolationsAreAllReported() {
        SchemaValidationException exception = catchThrowableOfType(
                () -> service.validate("test-entity", payload("A", 0)),
                SchemaValidationException.class);

        assertThat(exception.getDetails()).hasSize(2);
        assertThat(exception.getMessage()).contains("2 violations");
    }

    @Test
    void missingRequiredFieldIsAViolation() {
        SchemaValidationException exception = catchThrowableOfType(
                () -> service.validate("test-entity", new LinkedHashMap<String, Object>()),
                SchemaValidationException.class);

        assertThat(exception.getDetails()).anySatisfy(detail ->
                assertThat(detail.getConstraint()).isEqualTo("required"));
    }

    @Test
    void wrongTypeIsAViolation() {
        SchemaValidationException exception = catchThrowableOfType(
                () -> service.validate("test-entity", payload("Ada", "eleven")),
                SchemaValidationException.class);

        assertThat(exception.getDetails().get(0).getConstraint()).isEqualTo("type");
    }

    @Test
    void unknownSchemaNameIsAConfigurationError() {
        assertThatThrownBy(() -> service.validate("no-such-schema", payload("Ada", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-schema")
                .hasMessageContaining("test-entity");
    }
}
