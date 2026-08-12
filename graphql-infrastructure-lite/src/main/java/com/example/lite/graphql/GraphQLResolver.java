package com.example.lite.graphql;

import graphql.schema.DataFetchingEnvironment;

/**
 * The single contract of the lite infrastructure: one implementation resolves one
 * schema coordinate - a field on {@code Query} or {@code Mutation} (an operation) or,
 * more rarely, a field of any other object type.
 *
 * <p>Domain modules implement this once per operation and register the implementation
 * as a Spring bean; the {@link GraphQLDispatchController} discovers every bean, wires
 * it into the schema at startup and dispatches matching requests to it. Existing
 * {@code graphql.schema.DataFetcher} classes (the contract graphql-java-annotations
 * fetchers already implement) do not need to be rewritten - wrap them with
 * {@link GraphQLResolvers#query}, {@link GraphQLResolvers#mutation} or
 * {@link GraphQLResolvers#field} instead.
 *
 * <p>The resolver receives the full {@link DataFetchingEnvironment}, so it has access
 * to everything about the request: arguments, selection set, source object, context.
 */
public interface GraphQLResolver {

    /**
     * The GraphQL type owning the resolved field: {@code "Query"} or {@code "Mutation"}
     * for operations, or any object type name (e.g. {@code "Person"}) for a computed
     * field of that type.
     */
    String parentType();

    /** The field name at that type, e.g. {@code "personById"}. */
    String fieldName();

    /** Executes the operation (or computes the field) for one request. */
    Object resolve(DataFetchingEnvironment environment) throws Exception;

    /**
     * How this resolver appears in startup and dispatch logs. The default (the
     * implementing class's simple name) is right for hand-written resolvers;
     * {@link GraphQLResolvers} adapters override it to name the wrapped fetcher.
     */
    default String description() {
        return getClass().getSimpleName();
    }
}
