/**
 * The GraphQL controller layer: receives every query/mutation and routes it to the
 * matching resolver.
 *
 * <p>{@link com.example.infrastructure.graphql.dispatch.GraphQLResolver} is the
 * contract domain modules implement (one bean per operation);
 * {@link com.example.infrastructure.graphql.dispatch.GraphQLResolverRegistry} collects
 * and indexes those beans;
 * {@link com.example.infrastructure.graphql.dispatch.GraphQLDispatchController} wires
 * them into the schema at startup, logs each incoming operation, runs the declared
 * JSON Schema validations and dispatches.
 */
package com.example.infrastructure.graphql.dispatch;
