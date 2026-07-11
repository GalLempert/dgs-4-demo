package com.example.infrastructure.filter;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/**
 * Strategy for one filter predicate ({@code equals}, {@code like}, {@code between}...).
 * Each strategy knows which arguments it expects and how to turn them into a JPA
 * criteria {@link Predicate} on the given attribute path. Adding a predicate to the
 * system is adding one bean plus the matching field on the schema's filter input types.
 *
 * <p>The predicate name must match the field name used in the GraphQL filter inputs
 * ({@code StringFilter.like}, {@code IntFilter.between}...). Which predicates are
 * available per raw type is enforced by the schema itself - the strategies stay
 * type-agnostic and coerce values to the entity attribute's Java type.
 */
public interface FilterPredicateStrategy {

    String predicateName();

    Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                          CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer);
}
