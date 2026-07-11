package com.example.infrastructure.filter;

import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.TooManyResultsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class QueryResultCapTest {

    private final QueryResultCap cap = new QueryResultCap(10);

    @Test
    void countsAtOrBelowTheCapPass() {
        assertThatCode(() -> cap.enforce("Person", 0)).doesNotThrowAnyException();
        assertThatCode(() -> cap.enforce("Person", 10)).doesNotThrowAnyException();
    }

    @Test
    void countsAboveTheCapAreRejectedWithStructuredError() {
        TooManyResultsException exception = catchThrowableOfType(
                () -> cap.enforce("Person", 11), TooManyResultsException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_SET_TOO_LARGE);
        assertThat(exception.getErrorCode().httpStatus()).isEqualTo(422);
        assertThat(exception.getMessage()).contains("11").contains("10").contains("Person");
        assertThat(exception.getDetails()).hasSize(1);
        assertThat(exception.getDetails().get(0).getConstraint()).isEqualTo("max-results");
    }
}
