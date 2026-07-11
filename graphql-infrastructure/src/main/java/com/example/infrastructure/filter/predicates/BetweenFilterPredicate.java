package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/** {@code between: { from: X, to: Y }} (inclusive) - numeric and date fields. */
@Component
public class BetweenFilterPredicate extends ComparisonFilterPredicate {

    @Override
    public String predicateName() {
        return "between";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        return criteriaBuilder.between(comparablePath(path),
                comparableValue(arguments.get("from"), path, coercer),
                comparableValue(arguments.get("to"), path, coercer));
    }
}
