package com.example.lite.graphql;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GraphQLDispatchControllerTest {

    private static final String SDL = "type Query { hello: String } "
            + "extend type Query { extra: String } "
            + "type Mutation { doIt(input: String): String } "
            + "type Person { name: String }";

    private final TypeDefinitionRegistry schema = new SchemaParser().parse(SDL);

    private static GraphQLDispatchController controllerWith(List<GraphQLResolver> resolvers) {
        return new GraphQLDispatchController(new GraphQLResolverRegistry(resolvers));
    }

    private static DataFetcher<?> registeredFetcher(GraphQLCodeRegistry.Builder builder, String parent, String field) {
        GraphQLFieldDefinition definition = GraphQLFieldDefinition.newFieldDefinition()
                .name(field).type(Scalars.GraphQLString).build();
        return builder.build().getDataFetcher(FieldCoordinates.coordinates(parent, field), definition);
    }

    @Test
    void registersResolverAndDispatchesToIt() throws Exception {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(GraphQLResolvers.query("hello", environment -> "world")));
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

        controller.registerResolvers(builder, schema);

        DataFetcher<?> fetcher = registeredFetcher(builder, "Query", "hello");
        assertThat(fetcher.get(mock(DataFetchingEnvironment.class))).isEqualTo("world");
    }

    @Test
    void registersResolversOnEveryRootAndObjectType() throws Exception {
        GraphQLDispatchController controller = controllerWith(Arrays.asList(
                GraphQLResolvers.query("hello", environment -> "world"),
                GraphQLResolvers.mutation("doIt", environment -> "done"),
                GraphQLResolvers.field("Person", "name", environment -> "presented")));
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

        controller.registerResolvers(builder, schema);

        assertThat(registeredFetcher(builder, "Mutation", "doIt").get(mock(DataFetchingEnvironment.class)))
                .isEqualTo("done");
        assertThat(registeredFetcher(builder, "Person", "name").get(mock(DataFetchingEnvironment.class)))
                .isEqualTo("presented");
    }

    @Test
    void fieldContributedByTypeExtensionIsAccepted() throws Exception {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(GraphQLResolvers.query("extra", environment -> "extended")));
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

        controller.registerResolvers(builder, schema);

        assertThat(registeredFetcher(builder, "Query", "extra").get(mock(DataFetchingEnvironment.class)))
                .isEqualTo("extended");
    }

    @Test
    void resolverForUndeclaredSchemaFieldFailsAtStartup() {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(GraphQLResolvers.query("missing", environment -> null)));

        assertThatThrownBy(() -> controller.registerResolvers(GraphQLCodeRegistry.newCodeRegistry(), schema))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Query.missing");
    }

    @Test
    void resolverForUndeclaredTypeFailsAtStartup() {
        GraphQLDispatchController controller = controllerWith(
                Collections.singletonList(GraphQLResolvers.field("Ghost", "name", environment -> null)));

        assertThatThrownBy(() -> controller.registerResolvers(GraphQLCodeRegistry.newCodeRegistry(), schema))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ghost.name");
    }
}
