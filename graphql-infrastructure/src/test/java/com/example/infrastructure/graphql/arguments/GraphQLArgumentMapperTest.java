package com.example.infrastructure.graphql.arguments;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphQLArgumentMapperTest {

    private final GraphQLArgumentMapper mapper = new GraphQLArgumentMapper(new ObjectMapper());

    public static class SampleInput {
        public String name;
        public Integer count;
        public NestedInput nested;
    }

    public static class NestedInput {
        public String city;
    }

    private DataFetchingEnvironment envWithArgument(String name, Object value) {
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getArgument(name)).thenReturn(value);
        return environment;
    }

    @Test
    void convertsNestedArgumentMapToDto() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "Ada");
        raw.put("count", 3);
        raw.put("nested", Collections.singletonMap("city", "Tel Aviv"));

        SampleInput input = mapper.argument(envWithArgument("input", raw), "input", SampleInput.class);

        assertThat(input.name).isEqualTo("Ada");
        assertThat(input.count).isEqualTo(3);
        assertThat(input.nested.city).isEqualTo("Tel Aviv");
    }

    @Test
    void unmappableArgumentBecomesInvalidArgument() {
        Map<String, Object> raw = Collections.singletonMap("count", "not-a-number");

        ApiException exception = catchThrowableOfType(
                () -> mapper.argument(envWithArgument("input", raw), "input", SampleInput.class),
                ApiException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getDetails()).hasSize(1);
        assertThat(exception.getDetails().get(0).getConstraint()).isEqualTo("type-mapping");
    }

    @Test
    void parsesNumericIdArguments() {
        assertThat(mapper.longArgument(envWithArgument("id", "42"), "id")).isEqualTo(42L);
        assertThat(mapper.longArgument(envWithArgument("id", 7), "id")).isEqualTo(7L);
    }

    @Test
    void nonNumericIdIsRejectedWith400NotA500() {
        ApiException exception = catchThrowableOfType(
                () -> mapper.longArgument(envWithArgument("id", "not-a-number"), "id"),
                ApiException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).contains("whole number").contains("not-a-number");
    }

    @Test
    void convertsNumberArgumentsToBigDecimal() {
        assertThat(mapper.decimalArgument(envWithArgument("salary", 12.5d), "salary"))
                .isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(mapper.decimalArgument(envWithArgument("salary", 100), "salary"))
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void missingDecimalArgumentIsRejected() {
        assertThatThrownBy(() -> mapper.decimalArgument(envWithArgument("salary", null), "salary"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("required");
    }
}
