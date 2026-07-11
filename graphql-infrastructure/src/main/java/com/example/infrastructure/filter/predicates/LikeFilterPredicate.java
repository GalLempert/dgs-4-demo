package com.example.infrastructure.filter.predicates;

import com.example.infrastructure.filter.FilterPredicateStrategy;
import com.example.infrastructure.filter.FilterValueCoercer;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.Map;

/**
 * {@code like: { value: "Ada%" }} - SQL LIKE with the client-supplied pattern
 * ({@code %} any sequence, {@code _} one character). Only offered on String fields
 * by the schema.
 */
@Component
public class LikeFilterPredicate implements FilterPredicateStrategy {

    @Override
    public String predicateName() {
        return "like";
    }

    @Override
    public Predicate toPredicate(Path<?> path, Map<String, Object> arguments,
                                 CriteriaBuilder criteriaBuilder, FilterValueCoercer coercer) {
        String pattern = String.valueOf(coercer.coerce(arguments.get("value"), String.class));
        return criteriaBuilder.like(path.as(String.class), pattern);
    }
}
