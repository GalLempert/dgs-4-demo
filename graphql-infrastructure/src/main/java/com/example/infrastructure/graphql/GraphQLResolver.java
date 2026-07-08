package com.example.infrastructure.graphql;

import graphql.schema.DataFetchingEnvironment;

/**
 * Contract for a single GraphQL operation (one query or one mutation field).
 *
 * <p>Domain modules implement this interface once per operation and register the
 * implementation as a Spring bean. The {@link GraphQLDispatchController} discovers all
 * implementations, wires them into the GraphQL schema and dispatches each incoming
 * operation to the matching resolver.
 *
 * <p>The resolver receives the full {@link DataFetchingEnvironment}, so it has access to
 * everything about the request: arguments, selection set, context, execution id, etc.
 */
public interface GraphQLResolver {

    /** Whether this resolver handles a query or a mutation. */
    GraphQLOperationType operationType();

    /** The field name on the Query/Mutation root type, e.g. {@code "personById"}. */
    String fieldName();

    /**
     * Executes the operation. Runs with full access to the data fetching environment.
     */
    Object resolve(DataFetchingEnvironment environment) throws Exception;
}
