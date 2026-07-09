package com.example.infrastructure.error.mapping;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionMapperTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void supportsApiExceptionAndSubclasses() {
        assertThat(mapper.supports(new ApiException(ErrorCode.INTERNAL_ERROR, "x"))).isTrue();
        assertThat(mapper.supports(EntityNotFoundException.of("Person", 1))).isTrue();
    }

    @Test
    void rejectsForeignExceptions() {
        assertThat(mapper.supports(new RuntimeException("boom"))).isFalse();
        assertThat(mapper.supports(new IllegalStateException("nope"))).isFalse();
    }

    @Test
    void mapPassesTheExceptionThroughUnchanged() {
        ApiException original = EntityNotFoundException.of("Person", 42);
        assertThat(mapper.map(original)).isSameAs(original);
    }
}
