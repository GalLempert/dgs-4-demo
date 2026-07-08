package com.example.infrastructure.graphql;

import com.example.infrastructure.exception.EntityNotFoundException;
import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single GraphQL entry point ("controller" layer).
 *
 * <p>At startup it programmatically registers a data fetcher for every
 * {@link GraphQLResolver} bean found by the {@link GraphQLResolverRegistry}. At request
 * time it therefore knows exactly which query or mutation was received, logs it, and
 * dispatches to the corresponding resolver class, handing over the full
 * {@link DataFetchingEnvironment}.
 *
 * <p>This class is completely domain-agnostic: adding a new domain only requires new
 * {@link GraphQLResolver} beans plus the matching schema file - no change here.
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
        for (GraphQLResolver resolver : resolverRegistry.allResolvers()) {
            String parentType = resolver.operationType().parentTypeName();
            verifyFieldExistsInSchema(typeDefinitionRegistry, parentType, resolver);

            FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, resolver.fieldName());
            DataFetcher<Object> dataFetcher = environment -> dispatch(resolver, environment);
            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);

            log.info("Registered GraphQL {} '{}' -> {}",
                    resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        }
        return codeRegistryBuilder;
    }

    private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception {
        log.info("Received GraphQL {} '{}', dispatching to {}",
                resolver.operationType(), resolver.fieldName(), resolver.getClass().getSimpleName());
        try {
            return resolver.resolve(environment);
        } catch (EntityNotFoundException notFound) {
            // Translate the framework-neutral exception thrown by service layers into the
            // DGS exception that renders as a NOT_FOUND GraphQL error.
            throw new DgsEntityNotFoundException(notFound.getMessage());
        }
    }

    private void verifyFieldExistsInSchema(TypeDefinitionRegistry typeDefinitionRegistry,
                                           String parentType,
                                           GraphQLResolver resolver) {
        boolean declared = typeDefinitionRegistry.getType(parentType)
                .map(type -> declaresField(type, resolver.fieldName()))
                .orElse(false);
        if (!declared) {
            throw new IllegalStateException(String.format(
                    "%s resolves '%s.%s' but no such field is declared in the GraphQL schema",
                    resolver.getClass().getName(), parentType, resolver.fieldName()));
        }
    }

    private boolean declaresField(TypeDefinition<?> type, String fieldName) {
        if (!(type instanceof graphql.language.ObjectTypeDefinition)) {
            return false;
        }
        return ((graphql.language.ObjectTypeDefinition) type).getFieldDefinitions().stream()
                .anyMatch(field -> field.getName().equals(fieldName));
    }
}
