package com.example.infrastructure.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorModelTest {

    @Test
    void errorCodesMapToHttpStatusAndGraphQLType() {
        assertThat(ErrorCode.ENTITY_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.ENTITY_NOT_FOUND.graphqlErrorType()).isEqualTo("NOT_FOUND");
        assertThat(ErrorCode.SCHEMA_VALIDATION_FAILED.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.SCHEMA_VALIDATION_FAILED.graphqlErrorType()).isEqualTo("BAD_REQUEST");
        assertThat(ErrorCode.INVALID_ARGUMENT.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.DUPLICATE_RESOURCE.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.DUPLICATE_RESOURCE.graphqlErrorType()).isEqualTo("FAILED_PRECONDITION");
        assertThat(ErrorCode.RESULT_SET_TOO_LARGE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.RESULT_SET_TOO_LARGE.graphqlErrorType()).isEqualTo("FAILED_PRECONDITION");
        assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
        assertThat(ErrorCode.INTERNAL_ERROR.graphqlErrorType()).isEqualTo("INTERNAL");
    }

    @Test
    void onlyInternalErrorIsAServerFault() {
        assertThat(ErrorCode.INTERNAL_ERROR.isServerFault()).isTrue();
        assertThat(ErrorCode.ENTITY_NOT_FOUND.isServerFault()).isFalse();
        assertThat(ErrorCode.SCHEMA_VALIDATION_FAILED.isServerFault()).isFalse();
        assertThat(ErrorCode.RESULT_SET_TOO_LARGE.isServerFault()).isFalse();
    }

    @Test
    void errorDetailMapContainsAllFields() {
        Map<String, Object> map = new ErrorDetail("$.heightCm", "maximum", "too tall").toMap();
        assertThat(map).containsEntry("field", "$.heightCm")
                .containsEntry("constraint", "maximum")
                .containsEntry("reason", "too tall");
    }

    @Test
    void errorDetailMapSkipsNullFields() {
        Map<String, Object> map = new ErrorDetail(null, "NullPointerException", null).toMap();
        assertThat(map).containsOnlyKeys("constraint");
    }

    @Test
    void apiExceptionDefaultsToNoDetails() {
        ApiException exception = new ApiException(ErrorCode.INVALID_ARGUMENT, "bad input");
        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).isEqualTo("bad input");
    }

    @Test
    void apiExceptionDetailsAreImmutable() {
        ApiException exception = new ApiException(ErrorCode.INVALID_ARGUMENT, "bad",
                Arrays.asList(new ErrorDetail("f", "c", "r")));
        assertThatThrownBy(() -> exception.getDetails().add(new ErrorDetail("x", "y", "z")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entityNotFoundFactoryBuildsReadableMessage() {
        EntityNotFoundException exception = EntityNotFoundException.of("Person", 7L);
        assertThat(exception.getMessage()).isEqualTo("Person with id 7 was not found");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    void duplicateResourceCarriesUniqueConstraintDetail() {
        DuplicateResourceException exception =
                new DuplicateResourceException("email taken", "email");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        assertThat(exception.getDetails()).hasSize(1);
        assertThat(exception.getDetails().get(0).getField()).isEqualTo("email");
        assertThat(exception.getDetails().get(0).getConstraint()).isEqualTo("unique");
    }

    @Test
    void schemaValidationMessagePluralizesViolationCount() {
        com.example.infrastructure.validation.SchemaValidationException one =
                new com.example.infrastructure.validation.SchemaValidationException("s",
                        Collections.singletonList(new ErrorDetail("f", "c", "r")));
        com.example.infrastructure.validation.SchemaValidationException two =
                new com.example.infrastructure.validation.SchemaValidationException("s",
                        Arrays.asList(new ErrorDetail("f", "c", "r"), new ErrorDetail("f2", "c2", "r2")));

        assertThat(one.getMessage()).contains("1 violation)").doesNotContain("violations");
        assertThat(two.getMessage()).contains("2 violations");
        assertThat(one.getSchemaName()).isEqualTo("s");
        assertThat(one.getErrorCode()).isEqualTo(ErrorCode.SCHEMA_VALIDATION_FAILED);
    }
}
