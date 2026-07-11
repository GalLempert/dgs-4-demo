package com.example.infrastructure.filter;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses the raw GraphQL {@code filter} argument (nested maps, as graphql-java
 * delivers input objects) into a framework-neutral {@link FilterCriteria} tree.
 *
 * <p>Walking rule: inside a field's map, keys that are known predicate names are
 * filter leaves ({@code {equals: {value: ...}}}); a map with no predicate keys is a
 * nested object and is recursed into with a dotted path ({@code address.city}). The
 * GraphQL schema already guarantees this structure - the parser re-validates so it is
 * also safe to call programmatically.
 */
@Component
public class FilterParser {

    private static final Logger log = LoggerFactory.getLogger(FilterParser.class);

    private final FilterPredicateRegistry predicateRegistry;

    public FilterParser(FilterPredicateRegistry predicateRegistry) {
        this.predicateRegistry = predicateRegistry;
    }

    public FilterCriteria parse(Map<String, Object> rawFilter) {
        if (rawFilter == null || rawFilter.isEmpty()) {
            return FilterCriteria.none();
        }
        List<FieldFilter> filters = new ArrayList<>();
        walk(rawFilter, "", filters);
        FilterCriteria criteria = FilterCriteria.and(filters);
        log.debug("Parsed filter: {}", criteria);
        return criteria;
    }

    private void walk(Map<String, Object> node, String pathPrefix, List<FieldFilter> collected) {
        node.forEach((fieldName, value) -> {
            String path = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
            Map<String, Object> content = requireMap(value, path);

            boolean allPredicates = content.keySet().stream().allMatch(predicateRegistry::isPredicate);
            boolean nonePredicates = content.keySet().stream().noneMatch(predicateRegistry::isPredicate);

            if (allPredicates && !content.isEmpty()) {
                content.forEach((predicate, arguments) ->
                        collected.add(new FieldFilter(path, predicate, requireMap(arguments, path + "." + predicate))));
            } else if (nonePredicates) {
                walk(content, path, collected);
            } else {
                throw invalidFilter(path, "mixes predicates and nested fields");
            }
        });
    }

    private Map<String, Object> requireMap(Object value, String path) {
        if (!Map.class.isInstance(value)) {
            throw invalidFilter(path, "expected an object but got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private ApiException invalidFilter(String path, String reason) {
        return new ApiException(ErrorCode.INVALID_ARGUMENT,
                "Malformed filter at '" + path + "': " + reason,
                Collections.singletonList(new ErrorDetail(path, "filter-structure", reason)));
    }
}
