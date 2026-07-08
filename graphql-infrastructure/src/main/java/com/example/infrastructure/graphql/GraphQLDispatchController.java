package com.example.infrastructure.graphql;

import com.example.infrastructure.validation.JsonSchemaValidationService;
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

import java.util.Map;

/**
 * The single GraphQL entry point ("controller" layer).
 *
 * <p>At startup it programmatically registers a data fetcher for every
 * {@link GraphQLResolver} bean found by the {@link GraphQLResolverRegistry}. At request
 * time it therefore knows exactly which query or mutation was received, logs it,
 * enforces the resolver's declared JSON Schema validations on the raw arguments, and
 * dispatches to the corresponding resolver class, handing over the full
 * {@link DataFetchingEnvironment}.
 *
 * <p>Exceptions are deliberately NOT handled here - anything thrown below this point
 * (validation, service, DAL) is caught by the {@link GraphQLExceptionHandler}, the
 * global error boundary that renders structured GraphQL errors.
 *
 * <p>This class is completely domain-agnostic: adding a new domain only requires new
 * {@link GraphQLResolver} beans plus the matching schema file - no change here.
 */
@DgsComponent
public class GraphQLDispatchController {

    private static final Logger log = LoggerFactory.getLogger(GraphQLDispatchController.class);

    private final GraphQLResolverRegistry resolverRegistry;
    private final JsonSchemaValidationService jsonSchemaValidationService;

    public GraphQLDispatchController(GraphQLResolverRegistry resolverRegistry,
                                     JsonSchemaValidationService jsonSchemaValidationService) {
        this.resolverRegistry = resolverRegistry;
        this.jsonSchemaValidationService = jsonSchemaValidationService;
    }

    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registerResolvers(GraphQLCodeRegistry.Builder codeRegistryBuilder,
                                                         TypeDefinitionRegistry typeDefinitionRegistry) {
        for (GraphQLResolver resolver : resolverRegistry.allResolvers()) {
            String parentType = resolver.operationType().parentTypeName();
            verifyFieldExistsInSchema(typeDefinitionRegistry, parentType, resolver);

            FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, resolver.fieldName());
            DataFetcher<Object> dataFetcher = environment -> dispatch(resolver, environment);
            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);

            log.info("Registered GraphQL {} '{}' -> {}{}",
                    resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName(),
                    resolver.argumentJsonSchemas().isEmpty()
                            ? ""
                            : " (JSON schema validation: " + resolver.argumentJsonSchemas() + ")");
        }
        return codeRegistryBuilder;
    }

    private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception {
        log.info("Received GraphQL {} '{}', dispatching to {}",
                resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        log.debug("Arguments of '{}': {}", resolver.fieldName(), environment.getArguments());

        for (Map.Entry<String, String> validation : resolver.argumentJsonSchemas().entrySet()) {
            String argumentName = validation.getKey();
            String schemaName = validation.getValue();
            log.debug("Validating argument '{}' of '{}' against JSON schema '{}'",
                    argumentName, resolver.fieldName(), schemaName);
            jsonSchemaValidationService.validate(schemaName, environment.getArgument(argumentName));
        }

        Object result = resolver.resolve(environment);
        log.debug("Resolver {} completed for '{}'", resolver.getClass().getSimpleName(), resolver.fieldName());
        return result;
    }

    private void verifyFieldExistsInSchema(TypeDefinitionRegistry typeDefinitionRegistry,
                                           String parentType,
                                           GraphQLResolver resolver) {
        boolean declared = typeDefinitionRegistry.getType(parentType, ObjectTypeDefinition.class)
                .map(type -> type.getFieldDefinitions().stream()
                        .anyMatch(field -> field.getName().equals(resolver.fieldName())))
                .orElse(false);
        if (!declared) {
            throw new IllegalStateException(String.format(
                    "%s resolves '%s.%s' but no such field is declared in the GraphQL schema",
                    resolver.getClass().getName(), parentType, resolver.fieldName()));
        }
    }
}
