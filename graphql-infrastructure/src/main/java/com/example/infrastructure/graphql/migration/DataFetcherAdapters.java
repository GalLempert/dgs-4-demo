package com.example.infrastructure.graphql.migration;

import com.example.infrastructure.graphql.dispatch.GraphQLFieldResolver;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps a plain {@link DataFetcher} - the contract every graphql-java-annotations
 * fetcher already implements - as a framework resolver, so an existing code-first
 * service can migrate operation by operation without rewriting its fetchers first.
 *
 * <p>A legacy Query/Mutation registry class like
 * <pre>{@code
 * @GraphQLField
 * @GraphQLDataFetcher(PersonByIdFetcher.class)
 * public static Person personById(@GraphQLName("id") long id) { ... }
 * }</pre>
 * becomes one bean declaration reusing the fetcher unchanged:
 * <pre>{@code
 * @Bean
 * GraphQLResolver personById() {
 *     return DataFetcherAdapters.query("personById", new PersonByIdFetcher());
 * }
 * }</pre>
 *
 * <p>The adapted fetcher receives the same {@link DataFetchingEnvironment} it always
 * did, and gains the dispatch pipeline: per-operation logging, boot-time verification
 * that the coordinate exists in the SDL, optional JSON-schema validation via
 * {@link OperationAdapter#validating(String, String)}, and the global error boundary.
 */
public final class DataFetcherAdapters {

    private DataFetcherAdapters() {
    }

    /** Adapts a legacy fetcher serving a field on the {@code Query} root type. */
    public static OperationAdapter query(String fieldName, DataFetcher<?> dataFetcher) {
        return new OperationAdapter(GraphQLOperationType.QUERY, fieldName, dataFetcher);
    }

    /** Adapts a legacy fetcher serving a field on the {@code Mutation} root type. */
    public static OperationAdapter mutation(String fieldName, DataFetcher<?> dataFetcher) {
        return new OperationAdapter(GraphQLOperationType.MUTATION, fieldName, dataFetcher);
    }

    /**
     * Adapts a legacy fetcher serving a field of an object type (the
     * {@code @GraphQLDataFetcher}-on-an-entity-field case), e.g. {@code Person.age}.
     * Fields without a fetcher need no adapter at all - graphql-java resolves them
     * from the getter of the same name.
     */
    public static GraphQLFieldResolver field(String parentType, String fieldName, DataFetcher<?> dataFetcher) {
        Objects.requireNonNull(parentType, "parentType");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(dataFetcher, "dataFetcher");
        return new GraphQLFieldResolver() {
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
        };
    }

    /**
     * A legacy fetcher adapted to a root operation, with the same fluent JSON-schema
     * opt-in the factory-made resolvers offer:
     * {@code DataFetcherAdapters.mutation("createPerson", fetcher).validating("input", "person-create")}.
     */
    public static final class OperationAdapter implements GraphQLResolver {

        private final GraphQLOperationType operationType;
        private final String fieldName;
        private final DataFetcher<?> dataFetcher;
        private final Map<String, String> argumentJsonSchemas = new LinkedHashMap<>();

        private OperationAdapter(GraphQLOperationType operationType, String fieldName, DataFetcher<?> dataFetcher) {
            this.operationType = Objects.requireNonNull(operationType, "operationType");
            this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
            this.dataFetcher = Objects.requireNonNull(dataFetcher, "dataFetcher");
        }

        /** Declares that {@code argumentName} must pass {@code classpath:json-schema/<schemaName>.json}. */
        public OperationAdapter validating(String argumentName, String schemaName) {
            argumentJsonSchemas.put(argumentName, schemaName);
            return this;
        }

        @Override
        public GraphQLOperationType operationType() {
            return operationType;
        }

        @Override
        public String fieldName() {
            return fieldName;
        }

        @Override
        public Map<String, String> argumentJsonSchemas() {
            return Collections.unmodifiableMap(argumentJsonSchemas);
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) throws Exception {
            return dataFetcher.get(environment);
        }
    }
}
