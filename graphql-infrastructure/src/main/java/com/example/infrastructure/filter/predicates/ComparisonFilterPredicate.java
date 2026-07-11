package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;

import javax.persistence.criteria.Path;

/**
 * Base for predicates that compare against the attribute's natural ordering
 * (greaterThan, lessThan, between): centralizes the coercion of raw argument values
 * into the attribute's {@link Comparable} type and the matching path cast.
 */
abstract class ComparisonFilterPredicate implements FilterPredicateStrategy {

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Comparable comparableValue(Object rawValue, Path<?> path, FilterValueCoercer coercer) {
        return (Comparable) coercer.coerce(rawValue, path.getJavaType());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Path<Comparable> comparablePath(Path<?> path) {
        return (Path<Comparable>) path;
    }
}
