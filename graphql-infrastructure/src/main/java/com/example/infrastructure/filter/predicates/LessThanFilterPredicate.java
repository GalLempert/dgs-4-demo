package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/** {@code lessThan: { value: X }} - numeric and date fields. */
@Component
public class LessThanFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "lessThan";
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        Comparable value = (Comparable) coercer.coerce(arguments.get("value"), path.getJavaType());
        return criteriaBuilder.lessThan((Path<Comparable>) path, value);
    }
}
