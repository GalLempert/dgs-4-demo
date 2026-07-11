package com.example.infrastructure.filter;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a {@link FilterCriteria} into a Spring Data JPA {@link Specification} - the
 * dynamically built WHERE clause. Works for any entity: attribute paths are resolved
 * from the field paths ({@code address.city} walks the embedded address), values are
 * coerced to the attribute's Java type, and each predicate is built by its
 * {@link FilterPredicateStrategy}.
 */
@Component
public class FilterSpecificationBuilder {

    private final FilterPredicateRegistry predicateRegistry;
    private final FilterValueCoercer valueCoercer;

    public FilterSpecificationBuilder(FilterPredicateRegistry predicateRegistry,
                                      FilterValueCoercer valueCoercer) {
        this.predicateRegistry = predicateRegistry;
        this.valueCoercer = valueCoercer;
    }

    public <T> Specification<T> toSpecification(FilterCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            if (criteria.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            List<Predicate> predicates = criteria.getFilters().stream()
                    .map(filter -> toPredicate(filter, root, criteriaBuilder))
                    .collect(Collectors.toList());
            Predicate[] asArray = predicates.toArray(new Predicate[0]);
            return criteria.getCombinator() == FilterCriteria.Combinator.OR
                    ? criteriaBuilder.or(asArray)
                    : criteriaBuilder.and(asArray);
        };
    }

    private Predicate toPredicate(FieldFilter filter, Root<?> root, CriteriaBuilder criteriaBuilder) {
        Path<?> path = resolvePath(root, filter.getFieldPath());
        return predicateRegistry.strategy(filter.getPredicate())
                .toPredicate(path, filter.getArguments(), criteriaBuilder, valueCoercer);
    }

    private Path<?> resolvePath(Root<?> root, String fieldPath) {
        try {
            Path<?> path = root;
            for (String segment : fieldPath.split("\\.")) {
                path = path.get(segment);
            }
            return path;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "Field '" + fieldPath + "' is not filterable on this resource",
                    Collections.singletonList(new ErrorDetail(fieldPath, "unknown-field", e.getMessage())));
        }
    }
}
