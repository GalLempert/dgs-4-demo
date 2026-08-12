/**
 * The entire lite GraphQL infrastructure - four classes, deliberately nothing more.
 *
 * <p>The lifecycle it implements is the same one an in-house
 * graphql-java-annotations wrapper provides, minus the code-first schema generation:
 *
 * <ol>
 *   <li><b>Startup - load the schema.</b> The DGS starter collects every
 *       {@code classpath:schema/*.graphqls} file (including ones inside jars) and
 *       builds the executable schema from them.</li>
 *   <li><b>Startup - wire the resolvers.</b> {@link com.example.lite.graphql.GraphQLDispatchController}
 *       registers a data fetcher for every {@link com.example.lite.graphql.GraphQLResolver}
 *       bean at its schema coordinate, failing boot if the coordinate does not exist
 *       in the SDL.</li>
 *   <li><b>Request time - serve the API.</b> DGS serves {@code POST /graphql}
 *       (plus introspection and GraphiQL); the dispatch controller logs each
 *       operation and hands it to the matching resolver, which calls the domain's
 *       existing service and DAL layers.</li>
 * </ol>
 *
 * <p>This package is domain-agnostic and self-contained: it depends only on the DGS
 * starter, never on the full {@code graphql-infrastructure} module, so it can be
 * lifted into another codebase as-is. A domain service uses it exactly like the full
 * framework: depend on the module, write the SDL, declare one resolver bean per
 * operation - either implementing {@link com.example.lite.graphql.GraphQLResolver}
 * directly or wrapping an existing {@code graphql.schema.DataFetcher} with
 * {@link com.example.lite.graphql.GraphQLResolvers}. See {@code docs/LITE.md}.
 */
package com.example.lite.graphql;
