package com.example.infrastructure.graphql.dispatch;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every {@link GraphQLResolver} bean in the application context and indexes it
 * by its schema coordinate (e.g. {@code Query.personById}, {@code Mutation.createPerson}).
 *
 * <p>Fails fast at startup if two resolvers claim the same coordinate.
 */
@Component
public class GraphQLResolverRegistry {

    private final Map<String, GraphQLResolver> resolversByCoordinate;

    public GraphQLResolverRegistry(List<GraphQLResolver> resolvers) {
        Map<String, GraphQLResolver> index = new LinkedHashMap<>();
        for (GraphQLResolver resolver : resolvers) {
            String coordinate = coordinateOf(resolver);
            GraphQLResolver previous = index.put(coordinate, resolver);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "Duplicate GraphQL resolvers for '%s': %s and %s",
                        coordinate,
                        previous.getClass().getName(),
                        resolver.getClass().getName()));
            }
        }
        this.resolversByCoordinate = Collections.unmodifiableMap(index);
    }

    public Collection<GraphQLResolver> allResolvers() {
        return resolversByCoordinate.values();
    }

    private static String coordinateOf(GraphQLResolver resolver) {
        return resolver.operationType().parentTypeName() + "." + resolver.fieldName();
    }
}
