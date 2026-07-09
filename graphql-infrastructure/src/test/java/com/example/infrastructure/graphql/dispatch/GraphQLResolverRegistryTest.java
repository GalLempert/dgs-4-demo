package com.example.infrastructure.graphql.dispatch;

import com.example.infrastructure.graphql.format.IsoTemporalFormatter;
import com.example.infrastructure.graphql.format.TemporalFormatterRegistry;
import com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQLResolverRegistryTest {

    private static GraphQLResolver operation(GraphQLOperationType type, String field) {
        return new GraphQLResolver() {
            @Override
            public GraphQLOperationType operationType() {
                return type;
            }

            @Override
            public String fieldName() {
                return field;
            }

            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return field;
            }
        };
    }

    private static GraphQLFieldResolver field(String parentType, String field) {
        return new GraphQLFieldResolver() {
            @Override
            public String parentType() {
                return parentType;
            }

            @Override
            public String fieldName() {
                return field;
            }

            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return field;
            }
        };
    }

    private static AnnotatedFieldResolverFactory emptyFactory() {
        return new AnnotatedFieldResolverFactory(
                Collections.emptyList(),
                Collections.emptyList(),
                new TemporalFormatterRegistry(Collections.singletonList(new IsoTemporalFormatter())));
    }

    @Test
    void indexesOperationsAndFieldResolversSeparately() {
        GraphQLResolverRegistry registry = new GraphQLResolverRegistry(
                Arrays.asList(operation(GraphQLOperationType.QUERY, "personById"),
                        operation(GraphQLOperationType.MUTATION, "createPerson")),
                Collections.singletonList(field("Person", "gender")),
                emptyFactory());

        assertThat(registry.operationResolvers()).hasSize(2);
        assertThat(registry.fieldResolvers()).hasSize(1);
    }

    @Test
    void sameFieldNameOnDifferentParentsIsAllowed() {
        GraphQLResolverRegistry registry = new GraphQLResolverRegistry(
                Collections.singletonList(operation(GraphQLOperationType.QUERY, "name")),
                Arrays.asList(field("Person", "name"), field("Company", "name")),
                emptyFactory());

        assertThat(registry.fieldResolvers()).hasSize(2);
    }

    @Test
    void duplicateOperationCoordinatesFailFast() {
        List<GraphQLResolver> duplicates = Arrays.asList(
                operation(GraphQLOperationType.QUERY, "personById"),
                operation(GraphQLOperationType.QUERY, "personById"));

        assertThatThrownBy(() ->
                new GraphQLResolverRegistry(duplicates, Collections.emptyList(), emptyFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("Query.personById");
    }

    @Test
    void duplicateFieldCoordinatesFailFast() {
        List<GraphQLFieldResolver> duplicates = Arrays.asList(
                field("Person", "gender"), field("Person", "gender"));

        assertThatThrownBy(() ->
                new GraphQLResolverRegistry(Collections.emptyList(), duplicates, emptyFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Person.gender");
    }

    @Test
    void emptyRegistryIsLegal() {
        GraphQLResolverRegistry registry = new GraphQLResolverRegistry(
                Collections.emptyList(), Collections.emptyList(), emptyFactory());

        assertThat(registry.operationResolvers()).isEmpty();
        assertThat(registry.fieldResolvers()).isEmpty();
    }
}
