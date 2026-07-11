package com.example.infrastructure.filter;

import java.util.Collections;
import java.util.List;

/**
 * A parsed, framework-neutral filter: the list of {@link FieldFilter}s and how they
 * combine. The default combinator is {@link Combinator#AND} - a row must satisfy every
 * filter - which is the least surprising interpretation of "several filters at once".
 * OR is modeled and ready but not yet exposed through the GraphQL schema.
 */
public final class FilterCriteria {

    public enum Combinator {
        AND,
        OR
    }

    private static final FilterCriteria NONE = new FilterCriteria(Collections.emptyList(), Combinator.AND);

    private final List<FieldFilter> filters;
    private final Combinator combinator;

    private FilterCriteria(List<FieldFilter> filters, Combinator combinator) {
        this.filters = Collections.unmodifiableList(filters);
        this.combinator = combinator;
    }

    public static FilterCriteria none() {
        return NONE;
    }

    public static FilterCriteria and(List<FieldFilter> filters) {
        return new FilterCriteria(filters, Combinator.AND);
    }

    public static FilterCriteria of(List<FieldFilter> filters, Combinator combinator) {
        return new FilterCriteria(filters, combinator);
    }

    public List<FieldFilter> getFilters() {
        return filters;
    }

    public Combinator getCombinator() {
        return combinator;
    }

    public boolean isEmpty() {
        return filters.isEmpty();
    }

    @Override
    public String toString() {
        return combinator + " " + filters;
    }
}
