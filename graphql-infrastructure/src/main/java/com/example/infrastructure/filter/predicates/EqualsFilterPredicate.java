package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/** {@code equals: { value: X }} - available on every filterable type. */
@Component
public class EqualsFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "equals";
    }

    @Override
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        return criteriaBuilder.equal(path, coercer.coerce(arguments.get("value"), path.getJavaType()));
    }
}
