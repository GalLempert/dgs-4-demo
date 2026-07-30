package com.example.infrastructure.graphql.migration;

import com.example.infrastructure.graphql.dispatch.GraphQLFieldResolver;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.migration.DataFetcherAdapters.OperationAdapter;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class DataFetcherAdaptersTest {

    private static DataFetchingEnvironment environment() {
        return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build();
    }

    @Test
    void queryAdapterExposesCoordinateAndDelegatesToTheLegacyFetcher() throws Exception {
        AtomicReference<DataFetchingEnvironment> seen = new AtomicReference<>();
        DataFetcher<String> legacyFetcher = env -> {
            seen.set(env);
            return "legacy result";
        };

        OperationAdapter adapter = DataFetcherAdapters.query("personById", legacyFetcher);
        DataFetchingEnvironment environment = environment();

        assertThat(adapter.operationType()).isEqualTo(GraphQLOperationType.QUERY);
        assertThat(adapter.fieldName()).isEqualTo("personById");
        assertThat(adapter.argumentJsonSchemas()).isEmpty();
        assertThat(adapter.resolve(environment)).isEqualTo("legacy result");
        assertThat(seen.get()).isSameAs(environment);
    }

    @Test
    void mutationAdapterCollectsFluentValidationDeclarations() {
        OperationAdapter adapter = DataFetcherAdapters
                .mutation("createPerson", env -> "created")
                .validating("input", "person-create")
                .validating("updateInput", "person-update");

        assertThat(adapter.operationType()).isEqualTo(GraphQLOperationType.MUTATION);
        assertThat(adapter.argumentJsonSchemas()).containsExactly(
                entry("input", "person-create"),
                entry("updateInput", "person-update"));
    }

    @Test
    void fieldAdapterExposesTypeCoordinateAndDelegates() throws Exception {
        GraphQLFieldResolver adapter = DataFetcherAdapters.field("Person", "age", env -> 42);

        assertThat(adapter.parentType()).isEqualTo("Person");
        assertThat(adapter.fieldName()).isEqualTo("age");
        assertThat(adapter.resolve(environment())).isEqualTo(42);
    }

    @Test
    void legacyFetcherExceptionsPropagateToTheErrorBoundary() {
        OperationAdapter adapter = DataFetcherAdapters.query("boom", env -> {
            throw new IllegalStateException("legacy failure");
        });

        assertThatThrownBy(() -> adapter.resolve(environment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("legacy failure");
    }

    @Test
    void nullFetcherIsRejectedAtConstructionNotAtRequestTime() {
        assertThatThrownBy(() -> DataFetcherAdapters.query("personById", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DataFetcherAdapters.field("Person", "age", null))
                .isInstanceOf(NullPointerException.class);
    }
}
