package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.List;
import java.util.Map;

/** {@code in: { values: [X, Y] }}. */
@Component
public class InFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "in";
    }

    @Override
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        List<?> rawValues = (List<?>) arguments.get("values");
        return path.in(coercer.coerceAll(rawValues, path.getJavaType()));
    }
}
