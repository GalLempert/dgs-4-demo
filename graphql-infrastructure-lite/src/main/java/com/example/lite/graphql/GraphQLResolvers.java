package com.example.lite.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Objects;

/**
 * Adapts a plain {@link DataFetcher} - the interface every graphql-java-annotations
 * fetcher already implements - to a {@link GraphQLResolver}, so a migrating service
 * reuses its existing fetcher classes unchanged. A legacy registry entry like
 * <pre>{@code
 * @GraphQLField
 * @GraphQLDataFetcher(PersonByIdFetcher.class)
 * public static Person personById(@GraphQLName("id") long id) { ... }
 * }</pre>
 * becomes one bean declaration reusing the fetcher as-is:
 * <pre>{@code
 * @Bean
 * GraphQLResolver personById(PersonService service) {
 *     return GraphQLResolvers.query("personById", new PersonByIdFetcher(service));
 * }
 * }</pre>
 *
 * <p>Any {@code DataFetcher} works, including a lambda, so a brand-new operation can
 * be wired without a fetcher class at all:
 * {@code GraphQLResolvers.query("allPersons", env -> service.getAll())}.
 */
public final class GraphQLResolvers {

    private static final String QUERY_TYPE = "Query";
    private static final String MUTATION_TYPE = "Mutation";

    private GraphQLResolvers() {
    }

    /** Adapts a fetcher serving a field on the {@code Query} root type. */
    public static GraphQLResolver query(String fieldName, DataFetcher<?> dataFetcher) {
        return field(QUERY_TYPE, fieldName, dataFetcher);
    }

    /** Adapts a fetcher serving a field on the {@code Mutation} root type. */
    public static GraphQLResolver mutation(String fieldName, DataFetcher<?> dataFetcher) {
        return field(MUTATION_TYPE, fieldName, dataFetcher);
    }

    /**
     * Adapts a fetcher serving a field of any object type (the
     * {@code @GraphQLDataFetcher}-on-an-entity-field case), e.g. {@code Person.age}.
     * Fields without a fetcher need no resolver at all - graphql-java reads them from
     * the getter of the same name.
     */
    public static GraphQLResolver field(String parentType, String fieldName, DataFetcher<?> dataFetcher) {
        Objects.requireNonNull(parentType, "parentType");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(dataFetcher, "dataFetcher");
        return new GraphQLResolver() {
            @Override
            public String parentType() {
                return parentType;
            }

            @Override
            public String fieldName() {
                return fieldName;
            }

            @Override
            public Object resolve(DataFetchingEnvironment environment) throws Exception {
                return dataFetcher.get(environment);
            }

            @Override
            public String description() {
                return "adapter of " + fetcherName(dataFetcher);
            }
        };
    }

    private static String fetcherName(DataFetcher<?> dataFetcher) {
        String name = dataFetcher.getClass().getSimpleName();
        int lambdaMarker = name.indexOf("$$Lambda");
        if (lambdaMarker > 0) {
            // a lambda's class name is noise; keep only where it was declared
            return name.substring(0, lambdaMarker) + " lambda";
        }
        return name.isEmpty() ? dataFetcher.getClass().getName() : name;
    }
}
