package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/** {@code notEquals: { value: X }}. */
@Component
public class NotEqualsFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "notEquals";
    }

    @Override
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        return criteriaBuilder.notEqual(path, coercer.coerce(arguments.get("value"), path.getJavaType()));
    }
}
