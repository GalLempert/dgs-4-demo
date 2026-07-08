package com.example.infrastructure.graphql.dispatch;

import com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the GraphQL resolvers of the application and indexes them by schema
 * coordinate: {@link GraphQLResolver} beans for root operations
 * ({@code Query.personById}, {@code Mutation.createPerson}) and
 * {@link GraphQLFieldResolver}s for type-field presentation ({@code Person.gender}) -
 * both hand-written beans and the ones generated from model annotations by the
 * {@link AnnotatedFieldResolverFactory}.
 *
 * <p>Fails fast at startup if two resolvers claim the same coordinate.
 */
@Component
public class GraphQLResolverRegistry {

    private final Map<String, GraphQLResolver> operationsByCoordinate;
    private final Map<String, GraphQLFieldResolver> fieldResolversByCoordinate;

    public GraphQLResolverRegistry(List<GraphQLResolver> operationResolvers,
                                   List<GraphQLFieldResolver> fieldResolvers,
                                   AnnotatedFieldResolverFactory annotatedFieldResolverFactory) {
        Map<String, GraphQLResolver> operations = new LinkedHashMap<>();
        for (GraphQLResolver resolver : operationResolvers) {
            putUnique(operations, resolver.operationType().parentTypeName(), resolver.fieldName(), resolver);
        }
        this.operationsByCoordinate = Collections.unmodifiableMap(operations);

        List<GraphQLFieldResolver> allFieldResolvers = new ArrayList<>(fieldResolvers);
        allFieldResolvers.addAll(annotatedFieldResolverFactory.createResolvers());
        Map<String, GraphQLFieldResolver> fields = new LinkedHashMap<>();
        for (GraphQLFieldResolver resolver : allFieldResolvers) {
            putUnique(fields, resolver.parentType(), resolver.fieldName(), resolver);
        }
        this.fieldResolversByCoordinate = Collections.unmodifiableMap(fields);
    }

    public Collection<GraphQLResolver> operationResolvers() {
        return operationsByCoordinate.values();
    }

    public Collection<GraphQLFieldResolver> fieldResolvers() {
        return fieldResolversByCoordinate.values();
    }

    private static <T> void putUnique(Map<String, T> index, String parentType, String fieldName, T resolver) {
        String coordinate = parentType + "." + fieldName;
        T previous = index.put(coordinate, resolver);
        if (previous != null) {
            throw new IllegalStateException(String.format(
                    "Duplicate GraphQL resolvers for '%s': %s and %s",
                    coordinate,
                    previous.getClass().getName(),
                    resolver.getClass().getName()));
        }
    }
}
