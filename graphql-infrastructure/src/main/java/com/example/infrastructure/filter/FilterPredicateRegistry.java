package com.example.infrastructure.filter;

import com.example.infrastructure.support.UniqueIndex;
import org.springframework.stereotype.Component;

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
        this.strategiesByName = UniqueIndex.byKey(strategies,
                FilterPredicateStrategy::predicateName, "filter predicates");
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
