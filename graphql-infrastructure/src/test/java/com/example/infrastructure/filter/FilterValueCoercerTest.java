package com.example.infrastructure.filter;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FilterValueCoercerTest {

    enum Color { RED, GREEN }

    private final FilterValueCoercer coercer = new FilterValueCoercer(new ObjectMapper());

    @Test
    void coercesGraphQLFloatToEntityBigDecimal() {
        assertThat(coercer.coerce(12.5d, BigDecimal.class)).isEqualTo(new BigDecimal("12.5"));
    }

    @Test
    void coercesStringToEntityEnum() {
        assertThat(coercer.coerce("RED", Color.class)).isEqualTo(Color.RED);
    }

    @Test
    void coercesWholeListsForInPredicates() {
        assertThat(coercer.coerceAll(Arrays.asList(1, 2), Long.class)).containsExactly(1L, 2L);
    }

    @Test
    void impossibleCoercionIsAnInvalidArgument() {
        ApiException exception = catchThrowableOfType(
                () -> coercer.coerce("purple", Color.class), ApiException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).contains("purple").contains("Color");
    }
}
