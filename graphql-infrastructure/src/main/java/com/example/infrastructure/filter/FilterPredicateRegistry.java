package com.example.infrastructure.filter;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes the {@link FilterPredicateStrategy} beans by predicate name. Fails fast at
 * startup on duplicates; also tells the parser which map keys are predicates (vs.
 * nested filter objects).
 */
@Component
public class FilterPredicateRegistry {

    private final Map<String, FilterPredicateStrategy> strategiesByName;

    public FilterPredicateRegistry(List<FilterPredicateStrategy> strategies) {
        Map<String, FilterPredicateStrategy> index = new LinkedHashMap<>();
        for (FilterPredicateStrategy strategy : strategies) {
            FilterPredicateStrategy previous = index.put(strategy.predicateName(), strategy);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "Duplicate filter predicates for '%s': %s and %s",
                        strategy.predicateName(),
                        previous.getClass().getName(),
                        strategy.getClass().getName()));
            }
        }
        this.strategiesByName = Collections.unmodifiableMap(index);
    }

    public boolean isPredicate(String name) {
        return strategiesByName.containsKey(name);
    }

    public Set<String> predicateNames() {
        return strategiesByName.keySet();
    }

    public FilterPredicateStrategy strategy(String predicateName) {
        FilterPredicateStrategy strategy = strategiesByName.get(predicateName);
        if (strategy == null) {
            throw new IllegalStateException("No filter predicate named '" + predicateName
                    + "' (available: " + strategiesByName.keySet() + ")");
        }
        return strategy;
    }
}
