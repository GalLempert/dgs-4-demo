package com.example.infrastructure.filter;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.filter.predicates.BetweenFilterPredicate;
import com.example.infrastructure.filter.predicates.EqualsFilterPredicate;
import com.example.infrastructure.filter.predicates.LikeFilterPredicate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FilterParserTest {

    private final FilterParser parser = new FilterParser(new FilterPredicateRegistry(Arrays.asList(
            new EqualsFilterPredicate(), new LikeFilterPredicate(), new BetweenFilterPredicate())));

    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    void nullOrEmptyFilterParsesToNone() {
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse(Collections.emptyMap()).isEmpty()).isTrue();
        assertThat(parser.parse(null).getCombinator()).isEqualTo(FilterCriteria.Combinator.AND);
    }

    @Test
    void parsesSimpleFieldPredicate() {
        Map<String, Object> raw = map("firstName", map("equals", map("value", "Ada")));

        FilterCriteria criteria = parser.parse(raw);

        assertThat(criteria.getFilters()).hasSize(1);
        FieldFilter filter = criteria.getFilters().get(0);
        assertThat(filter.getFieldPath()).isEqualTo("firstName");
        assertThat(filter.getPredicate()).isEqualTo("equals");
        assertThat(filter.getArguments()).containsEntry("value", "Ada");
    }

    @Test
    void multiplePredicatesOnOneFieldBecomeSeparateFilters() {
        Map<String, Object> heightPredicates = new LinkedHashMap<>();
        heightPredicates.put("equals", map("value", 170));
        heightPredicates.put("between", mapOf("from", 150, "to", 190));
        Map<String, Object> raw = map("heightCm", heightPredicates);

        FilterCriteria criteria = parser.parse(raw);

        assertThat(criteria.getFilters()).hasSize(2);
        assertThat(criteria.getCombinator()).isEqualTo(FilterCriteria.Combinator.AND);
    }

    @Test
    void nestedObjectsProduceDottedPaths() {
        Map<String, Object> raw = map("address", map("city", map("equals", map("value", "Tel Aviv"))));

        FilterCriteria criteria = parser.parse(raw);

        assertThat(criteria.getFilters()).hasSize(1);
        assertThat(criteria.getFilters().get(0).getFieldPath()).isEqualTo("address.city");
    }

    @Test
    void multipleFieldsAllCollected() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("firstName", map("like", map("value", "A%")));
        raw.put("address", map("city", map("equals", map("value", "Haifa"))));

        assertThat(parser.parse(raw).getFilters())
                .extracting(FieldFilter::getFieldPath)
                .containsExactly("firstName", "address.city");
    }

    @Test
    void mixingPredicatesAndNestedFieldsIsRejected() {
        Map<String, Object> mixed = new LinkedHashMap<>();
        mixed.put("equals", map("value", "x"));
        mixed.put("city", map("equals", map("value", "y")));
        Map<String, Object> raw = map("address", mixed);

        ApiException exception = catchThrowableOfType(() -> parser.parse(raw), ApiException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).contains("address");
    }

    @Test
    void nonObjectFilterValueIsRejected() {
        Map<String, Object> raw = map("firstName", "Ada");

        ApiException exception = catchThrowableOfType(() -> parser.parse(raw), ApiException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessage()).contains("firstName");
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
