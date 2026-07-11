package com.example.infrastructure.graphql.dispatch;

import com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory;
import com.example.infrastructure.support.UniqueIndex;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
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
        this.operationsByCoordinate = UniqueIndex.byKey(operationResolvers,
                resolver -> resolver.operationType().parentTypeName() + "." + resolver.fieldName(),
                "GraphQL resolvers");

        List<GraphQLFieldResolver> allFieldResolvers = new ArrayList<>(fieldResolvers);
        allFieldResolvers.addAll(annotatedFieldResolverFactory.createResolvers());
        this.fieldResolversByCoordinate = UniqueIndex.byKey(allFieldResolvers,
                resolver -> resolver.parentType() + "." + resolver.fieldName(),
                "GraphQL resolvers");
    }

    public Collection<GraphQLResolver> operationResolvers() {
        return operationsByCoordinate.values();
    }

    public Collection<GraphQLFieldResolver> fieldResolvers() {
        return fieldResolversByCoordinate.values();
    }
}
