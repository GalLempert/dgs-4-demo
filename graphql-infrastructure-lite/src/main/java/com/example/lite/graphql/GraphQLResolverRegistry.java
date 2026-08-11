package com.example.lite.graphql;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every {@link GraphQLResolver} bean of the application and indexes it by
 * schema coordinate ({@code Query.personById}, {@code Mutation.createPerson},
 * {@code Person.fullName}). Fails fast at startup if two resolvers claim the same
 * coordinate - a wiring mistake that would otherwise silently shadow one of them.
 */
@Component
public class GraphQLResolverRegistry {

    private final Map<String, GraphQLResolver> resolversByCoordinate;

    public GraphQLResolverRegistry(List<GraphQLResolver> resolvers) {
        Map<String, GraphQLResolver> byCoordinate = new LinkedHashMap<>();
        for (GraphQLResolver resolver : resolvers) {
            String coordinate = resolver.parentType() + "." + resolver.fieldName();
            GraphQLResolver previous = byCoordinate.putIfAbsent(coordinate, resolver);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "Two GraphQL resolvers claim the coordinate '%s': %s and %s",
                        coordinate, previous.getClass().getName(), resolver.getClass().getName()));
            }
        }
        this.resolversByCoordinate = byCoordinate;
    }

    public Collection<GraphQLResolver> resolvers() {
        return Collections.unmodifiableCollection(resolversByCoordinate.values());
    }
}
