package com.example.lite.graphql;

import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQLResolverRegistryTest {

    private static GraphQLResolver resolver(String parentType, String fieldName) {
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
            public Object resolve(DataFetchingEnvironment environment) {
                return null;
            }
        };
    }

    @Test
    void indexesEveryResolver() {
        GraphQLResolverRegistry registry = new GraphQLResolverRegistry(Arrays.asList(
                resolver("Query", "personById"),
                resolver("Mutation", "createPerson"),
                resolver("Person", "fullName")));

        assertThat(registry.resolvers()).hasSize(3);
    }

    @Test
    void sameFieldNameUnderDifferentParentTypesIsAllowed() {
        GraphQLResolverRegistry registry = new GraphQLResolverRegistry(Arrays.asList(
                resolver("Query", "name"),
                resolver("Person", "name")));

        assertThat(registry.resolvers()).hasSize(2);
    }

    @Test
    void duplicateCoordinateFailsAtStartup() {
        assertThatThrownBy(() -> new GraphQLResolverRegistry(Arrays.asList(
                resolver("Query", "personById"),
                resolver("Query", "personById"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Query.personById");
    }

    @Test
    void emptyApplicationIsAllowed() {
        assertThat(new GraphQLResolverRegistry(Collections.emptyList()).resolvers()).isEmpty();
    }
}
