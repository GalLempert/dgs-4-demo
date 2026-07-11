package com.example.infrastructure.support;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds an immutable name-to-instance index from a collection of strategy beans,
 * failing fast at startup when two beans claim the same key. Shared by every registry
 * in the infrastructure (resolvers, temporal formatters, filter predicates).
 */
public final class UniqueIndex {

    private UniqueIndex() {
    }

    public static <T> Map<String, T> byKey(Collection<T> items, Function<T, String> keyOf, String description) {
        Map<String, T> index = new LinkedHashMap<>();
        for (T item : items) {
            String key = keyOf.apply(item);
            T previous = index.put(key, item);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "Duplicate %s for '%s': %s and %s",
                        description, key, previous.getClass().getName(), item.getClass().getName()));
            }
        }
        return Collections.unmodifiableMap(index);
    }
}
