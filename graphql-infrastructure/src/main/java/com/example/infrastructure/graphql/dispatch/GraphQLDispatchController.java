package com.example.infrastructure.graphql.dispatch;

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

import java.util.Collections;
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
 * (validation, service, DAL) is caught by the {@link com.example.infrastructure.graphql.error.GraphQLExceptionHandler}, the
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
        registerOperationResolvers(codeRegistryBuilder, typeDefinitionRegistry);
        registerFieldResolvers(codeRegistryBuilder, typeDefinitionRegistry);
        return codeRegistryBuilder;
    }

    private void registerOperationResolvers(GraphQLCodeRegistry.Builder codeRegistryBuilder,
                                            TypeDefinitionRegistry typeDefinitionRegistry) {
        for (GraphQLResolver resolver : resolverRegistry.operationResolvers()) {
            String parentType = resolver.operationType().parentTypeName();
            verifyFieldExistsInSchema(typeDefinitionRegistry, parentType, resolver.fieldName(), resolver.getClass());

            FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, resolver.fieldName());
            DataFetcher<Object> dataFetcher = environment -> dispatch(resolver, environment);
            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);

            log.info("Registered GraphQL {} '{}' -> {}{}",
                    resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName(),
                    resolver.argumentJsonSchemas().isEmpty()
                            ? ""
                            : " (JSON schema validation: " + resolver.argumentJsonSchemas() + ")");
        }
    }

    private void registerFieldResolvers(GraphQLCodeRegistry.Builder codeRegistryBuilder,
                                        TypeDefinitionRegistry typeDefinitionRegistry) {
        for (GraphQLFieldResolver resolver : resolverRegistry.fieldResolvers()) {
            verifyFieldExistsInSchema(typeDefinitionRegistry, resolver.parentType(), resolver.fieldName(),
                    resolver.getClass());

            FieldCoordinates coordinates = FieldCoordinates.coordinates(resolver.parentType(), resolver.fieldName());
            DataFetcher<Object> dataFetcher = environment -> dispatchField(resolver, environment);
            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);

            log.info("Registered GraphQL field presentation '{}.{}' -> {}",
                    resolver.parentType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        }
    }

    private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception {
        log.info("Received GraphQL {} '{}', dispatching to {}",
                resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        log.debug("Arguments of '{}': {}", resolver.fieldName(), environment.getArguments());

        for (Map.Entry<String, String> validation : resolver.argumentJsonSchemas().entrySet()) {
            String argumentName = validation.getKey();
            String schemaName = validation.getValue();
            Object argumentValue = environment.getArgument(argumentName);
            if (argumentValue == null) {
                // an absent optional argument has nothing to validate; required-ness
                // is the GraphQL type system's job (non-null argument types)
                log.debug("Skipping JSON schema '{}' for absent argument '{}' of '{}'",
                        schemaName, argumentName, resolver.fieldName());
                continue;
            }
            log.debug("Validating argument '{}' of '{}' against JSON schema '{}'",
                    argumentName, resolver.fieldName(), schemaName);
            jsonSchemaValidationService.validate(schemaName, argumentValue);
        }

        Object result = resolver.resolve(environment);
        log.debug("Resolver {} completed for '{}'", resolver.getClass().getSimpleName(), resolver.fieldName());
        return result;
    }

    // field presentation runs per row of a result - log quietly
    private Object dispatchField(GraphQLFieldResolver resolver, DataFetchingEnvironment environment) throws Exception {
        log.debug("Presenting field '{}.{}' via {}",
                resolver.parentType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        return resolver.resolve(environment);
    }

    /**
     * The field may be declared on the base type or contributed by an
     * {@code extend type} block - domain modules other than the one declaring the base
     * Query/Mutation type add their operations through extensions.
     */
    private void verifyFieldExistsInSchema(TypeDefinitionRegistry typeDefinitionRegistry,
                                           String parentType,
                                           String fieldName,
                                           Class<?> resolverClass) {
        boolean declared = typeDefinitionRegistry.getType(parentType, ObjectTypeDefinition.class)
                .map(type -> hasField(type, fieldName))
                .orElse(false)
                || typeDefinitionRegistry.objectTypeExtensions()
                        .getOrDefault(parentType, Collections.emptyList()).stream()
                        .anyMatch(extension -> hasField(extension, fieldName));
        if (!declared) {
            throw new IllegalStateException(String.format(
                    "%s resolves '%s.%s' but no such field is declared in the GraphQL schema",
                    resolverClass.getName(), parentType, fieldName));
        }
    }

    private static boolean hasField(ObjectTypeDefinition type, String fieldName) {
        return type.getFieldDefinitions().stream().anyMatch(field -> field.getName().equals(fieldName));
    }
}
