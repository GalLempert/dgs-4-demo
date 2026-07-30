/**
 * Transitional adapters for migrating an existing code-first graphql-java service
 * (e.g. one built on graphql-java-annotations) onto this framework without rewriting
 * every data fetcher up front.
 *
 * <p>Any class implementing {@code graphql.schema.DataFetcher} - which is exactly what
 * a graphql-java-annotations {@code @GraphQLDataFetcher} references - can be wired to a
 * schema coordinate in one line via {@link
 * com.example.infrastructure.graphql.migration.DataFetcherAdapters}, gaining the
 * dispatch controller's per-operation logging, optional JSON-schema validation and
 * boot-time schema verification for free.
 *
 * <p>These adapters are a bridge, not a destination: once an operation stabilizes,
 * fold the legacy fetcher into a first-class
 * {@link com.example.infrastructure.graphql.dispatch.GraphQLResolver} implementation
 * and delete the adapter line. See {@code docs/MIGRATION-FROM-GRAPHQL-JAVA-ANNOTATIONS.md}.
 */
package com.example.infrastructure.graphql.migration;
