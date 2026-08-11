package com.example.lite.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GraphQLResolversTest {

    private final DataFetcher<String> fetcher = environment -> "fetched";

    @Test
    void queryAdapterTargetsTheQueryRootType() throws Exception {
        GraphQLResolver resolver = GraphQLResolvers.query("personById", fetcher);

        assertThat(resolver.parentType()).isEqualTo("Query");
        assertThat(resolver.fieldName()).isEqualTo("personById");
        assertThat(resolver.resolve(mock(DataFetchingEnvironment.class))).isEqualTo("fetched");
    }

    @Test
    void mutationAdapterTargetsTheMutationRootType() throws Exception {
        GraphQLResolver resolver = GraphQLResolvers.mutation("createPerson", fetcher);

        assertThat(resolver.parentType()).isEqualTo("Mutation");
        assertThat(resolver.fieldName()).isEqualTo("createPerson");
        assertThat(resolver.resolve(mock(DataFetchingEnvironment.class))).isEqualTo("fetched");
    }

    @Test
    void fieldAdapterTargetsAnyObjectType() throws Exception {
        GraphQLResolver resolver = GraphQLResolvers.field("Person", "age", fetcher);

        assertThat(resolver.parentType()).isEqualTo("Person");
        assertThat(resolver.fieldName()).isEqualTo("age");
        assertThat(resolver.resolve(mock(DataFetchingEnvironment.class))).isEqualTo("fetched");
    }

    @Test
    void adaptedFetcherReceivesTheSameEnvironment() throws Exception {
        @SuppressWarnings("unchecked")
        DataFetcher<Object> delegate = mock(DataFetcher.class);
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);

        GraphQLResolvers.query("anything", delegate).resolve(environment);

        verify(delegate).get(environment);
    }

    @Test
    void nullArgumentsAreRejectedUpFront() {
        assertThatNullPointerException()
                .isThrownBy(() -> GraphQLResolvers.query(null, fetcher));
        assertThatNullPointerException()
                .isThrownBy(() -> GraphQLResolvers.query("field", null));
        assertThatNullPointerException()
                .isThrownBy(() -> GraphQLResolvers.field(null, "field", fetcher));
    }
}
