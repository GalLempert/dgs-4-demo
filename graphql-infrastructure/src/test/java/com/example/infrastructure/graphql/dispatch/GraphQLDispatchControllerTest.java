package com.example.infrastructure.graphql.dispatch;

import com.example.infrastructure.graphql.format.IsoTemporalFormatter;
import com.example.infrastructure.graphql.format.TemporalFormatterRegistry;
import com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory;
import com.example.infrastructure.validation.JsonSchemaValidationService;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphQLDispatchControllerTest {

    private static final String SDL = "type Query { hello: String } "
            + "type Mutation { doIt(input: String): String } "
            + "type Person { name: String }";

    private final TypeDefinitionRegistry schema = new SchemaParser().parse(SDL);
    private final JsonSchemaValidationService validation = mock(JsonSchemaValidationService.class);

    private static GraphQLResolver operation(GraphQLOperationType type, String field,
                                             Map<String, String> schemas, Object result) {
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
            public Map<String, String> argumentJsonSchemas() {
                return schemas;
            }

            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return result;
            }
        };
    }

    private static GraphQLFieldResolver field(String parentType, String field, Object result) {
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
                return result;
            }
        };
    }

    private GraphQLDispatchController controllerWith(List<GraphQLResolver> operations,
                                                     List<GraphQLFieldResolver> fields) {
        AnnotatedFieldResolverFactory emptyFactory = new AnnotatedFieldResolverFactory(
                Collections.emptyList(), Collections.emptyList(),
                new TemporalFormatterRegistry(Collections.singletonList(new IsoTemporalFormatter())));
        return new GraphQLDispatchController(
                new GraphQLResolverRegistry(operations, fields, emptyFactory), validation);
    }

    private DataFetcher<?> registeredFetcher(GraphQLCodeRegistry.Builder builder, String parent, String field) {
        GraphQLFieldDefinition definition = GraphQLFieldDefinition.newFieldDefinition()
                .name(field).type(Scalars.GraphQLString).build();
        return builder.build().getDataFetcher(FieldCoordinates.coordinates(parent, field), definition);
    }

    @Test
    void registersOperationAndDispatchesToIt() throws Exception {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(
                        operation(GraphQLOperationType.QUERY, "hello", Collections.emptyMap(), "world")),
                Collections.emptyList());
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

        controller.registerResolvers(builder, schema);

        DataFetcher<?> fetcher = registeredFetcher(builder, "Query", "hello");
        assertThat(fetcher.get(mock(DataFetchingEnvironment.class))).isEqualTo("world");
    }

    @Test
    void validatesDeclaredArgumentsBeforeDispatching() throws Exception {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(operation(GraphQLOperationType.MUTATION, "doIt",
                        Collections.singletonMap("input", "my-schema"), "done")),
                Collections.emptyList());
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();
        controller.registerResolvers(builder, schema);

        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getArgument("input")).thenReturn("payload");

        Object result = registeredFetcher(builder, "Mutation", "doIt").get(environment);

        assertThat(result).isEqualTo("done");
        verify(validation).validate("my-schema", "payload");
    }

    @Test
    void registersFieldResolversOnArbitraryTypes() throws Exception {
        GraphQLDispatchController controller = controllerWith(
                Collections.emptyList(),
                Collections.singletonList(field("Person", "name", "presented")));
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

        controller.registerResolvers(builder, schema);

        DataFetcher<?> fetcher = registeredFetcher(builder, "Person", "name");
        assertThat(fetcher.get(mock(DataFetchingEnvironment.class))).isEqualTo("presented");
    }

    @Test
    void operationForUndeclaredSchemaFieldFailsAtStartup() {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(
                        operation(GraphQLOperationType.QUERY, "missing", Collections.emptyMap(), null)),
                Collections.emptyList());

        assertThatThrownBy(() -> controller.registerResolvers(GraphQLCodeRegistry.newCodeRegistry(), schema))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Query.missing");
    }

    @Test
    void fieldResolverForUndeclaredTypeFailsAtStartup() {
        GraphQLDispatchController controller = controllerWith(
                Collections.emptyList(),
                Collections.singletonList(field("Ghost", "name", null)));

        assertThatThrownBy(() -> controller.registerResolvers(GraphQLCodeRegistry.newCodeRegistry(), schema))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ghost.name");
    }
}
