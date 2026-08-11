package com.example.lite.graphql;

import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsComponent;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * The single GraphQL entry point ("controller" layer) of the lite infrastructure.
 *
 * <p>At startup its {@link DgsCodeRegistry} method runs against the schema the DGS
 * starter loaded from {@code classpath:schema/*.graphqls} and programmatically
 * registers a data fetcher for every {@link GraphQLResolver} bean found by the
 * {@link GraphQLResolverRegistry} - verifying first that the resolver's coordinate
 * exists in the SDL, so a typo fails boot instead of producing a dead field. At
 * request time it logs which operation was received and dispatches to the matching
 * resolver, handing over the full {@link DataFetchingEnvironment}.
 *
 * <p>Exceptions are deliberately not handled here: the lite track ships no error
 * boundary, so anything a resolver throws is rendered by DGS's default exception
 * handler - an {@code INTERNAL} error whose message includes the exception's class
 * name and message, so internals DO leak to clients. Register a DGS
 * {@code DataFetcherExceptionHandler} bean to mask them, or plug in the full
 * framework's {@code GraphQLExceptionHandler} approach for structured errors.
 *
 * <p>This class is completely domain-agnostic: adding a new domain (or a whole new
 * service on this module) only requires new {@link GraphQLResolver} beans plus the
 * matching schema file - no change here.
 */
@DgsComponent
public class GraphQLDispatchController {

    private static final Logger log = LoggerFactory.getLogger(GraphQLDispatchController.class);

    private final GraphQLResolverRegistry resolverRegistry;

    public GraphQLDispatchController(GraphQLResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registerResolvers(GraphQLCodeRegistry.Builder codeRegistryBuilder,
                                                         TypeDefinitionRegistry typeDefinitionRegistry) {
        for (GraphQLResolver resolver : resolverRegistry.resolvers()) {
            verifyFieldExistsInSchema(typeDefinitionRegistry, resolver);

            FieldCoordinates coordinates = FieldCoordinates.coordinates(resolver.parentType(), resolver.fieldName());
            DataFetcher<Object> dataFetcher = environment -> dispatch(resolver, environment);
            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);

            log.info("Registered GraphQL resolver '{}.{}' -> {}",
                    resolver.parentType(), resolver.fieldName(), resolver.description());
        }
        return codeRegistryBuilder;
    }

    private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception {
        if (isRootOperation(resolver)) {
            log.info("Received GraphQL operation '{}.{}', dispatching to {}",
                    resolver.parentType(), resolver.fieldName(), resolver.description());
            if (log.isDebugEnabled()) {
                // copy: graphql-java's argument map does not override toString()
                log.debug("Arguments of '{}': {}", resolver.fieldName(),
                        new LinkedHashMap<>(environment.getArguments()));
            }
        } else {
            // type fields run once per row of a result - log quietly
            log.debug("Resolving field '{}.{}' via {}",
                    resolver.parentType(), resolver.fieldName(), resolver.description());
        }
        return resolver.resolve(environment);
    }

    private static boolean isRootOperation(GraphQLResolver resolver) {
        return "Query".equals(resolver.parentType()) || "Mutation".equals(resolver.parentType());
    }

    /**
     * The field may be declared on the base type or contributed by an
     * {@code extend type} block - modules other than the one declaring the base
     * Query/Mutation type add their operations through extensions.
     */
    private void verifyFieldExistsInSchema(TypeDefinitionRegistry typeDefinitionRegistry, GraphQLResolver resolver) {
        String parentType = resolver.parentType();
        String fieldName = resolver.fieldName();
        boolean declared = typeDefinitionRegistry.getType(parentType, ObjectTypeDefinition.class)
                .map(type -> hasField(type, fieldName))
                .orElse(false)
                || typeDefinitionRegistry.objectTypeExtensions()
                        .getOrDefault(parentType, Collections.emptyList()).stream()
                        .anyMatch(extension -> hasField(extension, fieldName));
        if (!declared) {
            throw new IllegalStateException(String.format(
                    "Resolver %s resolves '%s.%s' but no such field is declared in the GraphQL schema",
                    resolver.description(), parentType, fieldName));
        }
    }

    private static boolean hasField(ObjectTypeDefinition type, String fieldName) {
        return type.getFieldDefinitions().stream().anyMatch(field -> field.getName().equals(fieldName));
    }
}
