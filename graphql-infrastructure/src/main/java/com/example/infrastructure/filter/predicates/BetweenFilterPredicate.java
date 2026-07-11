package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/** {@code between: { from: X, to: Y }} (inclusive) - numeric and date fields. */
@Component
public class BetweenFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "between";
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        Comparable from = (Comparable) coercer.coerce(arguments.get("from"), path.getJavaType());
        Comparable to = (Comparable) coercer.coerce(arguments.get("to"), path.getJavaType());
        return criteriaBuilder.between((Path<Comparable>) path, from, to);
    }
}
