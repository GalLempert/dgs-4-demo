package com.example.infrastructure.replication;

import com.example.infrastructure.filter.FilterParser;
import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manufactures the standard mutation resolvers of a replicated resource - the write
 * counterpart of {@link ReplicationResolverFactory}. A domain module never writes
 * resolver classes for the standard mutations either; it declares the schema fields
 * and registers one bean per field:
 *
 * <pre>{@code
 * @Bean GraphQLResolver createCompany(MutationResolverFactory f, CompanyService s) {
 *     return f.saveNew("createCompany", s, CreateCompanyInput.class);
 * }
 * @Bean GraphQLResolver updateCompanies(MutationResolverFactory f, CompanyService s) {
 *     return f.updateByFilter("updateCompanies", s, CreateCompanyInput.class);
 * }
 * @Bean GraphQLResolver saveOrUpdateCompany(MutationResolverFactory f, CompanyService s) {
 *     return f.saveOrUpdate("saveOrUpdateCompany", s, CreateCompanyInput.class, CreateCompanyInput.class);
 * }
 * @Bean GraphQLResolver saveOrOverrideCompany(MutationResolverFactory f, CompanyService s) {
 *     return f.saveOrOverride("saveOrOverrideCompany", s, CreateCompanyInput.class);
 * }
 * @Bean GraphQLResolver deleteCompanies(MutationResolverFactory f, CompanyService s) {
 *     return f.deleteByFilter("deleteCompanies", s);
 * }
 * }</pre>
 *
 * <p>The filtered mutations take the SAME filter argument, parsed by the same
 * {@link FilterParser}, as the standard queries - one filter language across the whole
 * API. JSON-schema validation is opted into fluently:
 * {@code f.saveNew(...).validating("input", "person-create")}.
 *
 * <p>The dispatch controller still verifies each field name against the SDL at boot,
 * so a typo in either the schema or the bean declaration fails fast as usual.
 */
@Component
public class MutationResolverFactory {

    private final FilterParser filterParser;
    private final GraphQLArgumentMapper argumentMapper;

    public MutationResolverFactory(FilterParser filterParser, GraphQLArgumentMapper argumentMapper) {
        this.filterParser = filterParser;
        this.argumentMapper = argumentMapper;
    }

    /** {@code <createResource>(input)} - save a new resource built from the input. */
    public MutationResolver saveNew(String fieldName, ResourceService<?, ?> service, Class<?> inputType) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return service.saveNew(argumentMapper.argument(environment, "input", inputType));
            }
        };
    }

    /** {@code <updateResources>(filter, input)} - merge the input onto every matching resource. */
    public MutationResolver updateByFilter(String fieldName, ResourceService<?, ?> service, Class<?> inputType) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return service.update(filterParser.parse(environment.getArgument("filter")),
                        argumentMapper.argument(environment, "input", inputType));
            }
        };
    }

    /**
     * {@code <saveOrUpdateResource>(filter, input, updateInput)} - the upsert
     * orchestrator: creates from {@code input} when nothing matches the filter,
     * otherwise updates the matches with {@code updateInput} (or {@code input} when
     * the optional {@code updateInput} is absent).
     */
    public MutationResolver saveOrUpdate(String fieldName, ResourceService<?, ?> service,
                                         Class<?> inputType, Class<?> updateInputType) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                Object updateInput = environment.getArgument("updateInput") == null
                        ? null
                        : argumentMapper.argument(environment, "updateInput", updateInputType);
                return service.saveOrUpdate(filterParser.parse(environment.getArgument("filter")),
                        argumentMapper.argument(environment, "input", inputType),
                        updateInput);
            }
        };
    }

    /**
     * {@code <saveOrOverrideResource>(input)} - creates when the input's natural key
     * is unknown, otherwise replaces the existing row's whole business state with the
     * input. The service decides via its {@code naturalKeyOf()} hook.
     */
    public MutationResolver saveOrOverride(String fieldName, ResourceService<?, ?> service, Class<?> inputType) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return service.saveOrOverride(argumentMapper.argument(environment, "input", inputType));
            }
        };
    }

    /** {@code <deleteResources>(filter)} - soft-delete every matching resource, returns the count. */
    public MutationResolver deleteByFilter(String fieldName, ResourceService<?, ?> service) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return service.deleteByFilter(filterParser.parse(environment.getArgument("filter")));
            }
        };
    }

    /** {@code <deleteResource>(id)} - the classic id-based soft delete, returns whether a row was hit. */
    public MutationResolver deleteById(String fieldName, ResourceService<?, ?> service) {
        return new MutationResolver(fieldName) {
            @Override
            public Object resolve(DataFetchingEnvironment environment) {
                return service.softDelete(argumentMapper.longArgument(environment, "id"));
            }
        };
    }

    /**
     * Common shape of the manufactured resolvers: always a Mutation field, with
     * fluent opt-in to JSON-schema validation of arguments (enforced by the dispatch
     * controller before the resolver runs, like on any hand-written resolver).
     */
    public abstract static class MutationResolver implements GraphQLResolver {

        private final String fieldName;
        private final Map<String, String> argumentJsonSchemas = new LinkedHashMap<>();

        MutationResolver(String fieldName) {
            this.fieldName = fieldName;
        }

        /** Declares that {@code argumentName} must pass {@code classpath:json-schema/<schemaName>.json}. */
        public MutationResolver validating(String argumentName, String schemaName) {
            argumentJsonSchemas.put(argumentName, schemaName);
            return this;
        }

        @Override
        public final GraphQLOperationType operationType() {
            return GraphQLOperationType.MUTATION;
        }

        @Override
        public final String fieldName() {
            return fieldName;
        }

        @Override
        public final Map<String, String> argumentJsonSchemas() {
            return Collections.unmodifiableMap(argumentJsonSchemas);
        }
    }
}
