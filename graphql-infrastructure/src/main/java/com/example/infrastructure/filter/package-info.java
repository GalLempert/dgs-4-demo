/**
 * Generic, schema-driven filtering: the GraphQL layer parses the {@code filter}
 * argument into a {@link com.example.infrastructure.filter.FilterCriteria} tree
 * (fields, predicates, arguments; AND is the default combinator), and the DAL turns it
 * into a dynamically built WHERE clause via
 * {@link com.example.infrastructure.filter.FilterSpecificationBuilder} (JPA criteria
 * {@code Specification}). Predicates ({@code equals}, {@code like}, {@code between}…)
 * are {@link com.example.infrastructure.filter.FilterPredicateStrategy} beans - adding
 * one is adding a bean plus a schema field. Which predicates each raw type offers is
 * declared by the shared filter input types in {@code schema/common.graphqls}.
 *
 * <p>{@link com.example.infrastructure.filter.QueryResultCap} guards non-paginated
 * queries: count first, reject with RESULT_SET_TOO_LARGE when over the configured cap.
 */
package com.example.infrastructure.filter;
