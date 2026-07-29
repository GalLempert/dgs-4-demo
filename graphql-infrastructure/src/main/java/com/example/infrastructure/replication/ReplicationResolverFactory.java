package com.example.infrastructure.replication;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterParser;
import com.example.infrastructure.graphql.arguments.GraphQLArgumentMapper;
import com.example.infrastructure.graphql.dispatch.GraphQLOperationType;
import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Component;

/**
 * Manufactures the four standard query resolvers of a replicated resource, so a
 * domain module never writes resolver classes for them - it declares the schema
 * fields and registers one bean per field:
 *
 * <pre>{@code
 * @Bean GraphQLResolver companies(ReplicationResolverFactory f, CompanyService s) {
 *     return f.filteredList("companies", s);
 * }
 * @Bean GraphQLResolver companiesBySequence(ReplicationResolverFactory f, CompanyService s) {
 *     return f.bySequence("companiesBySequence", s);
 * }
 * @Bean GraphQLResolver countCompaniesByFilter(ReplicationResolverFactory f, CompanyService s) {
 *     return f.countByFilter("countCompaniesByFilter", s);
 * }
 * @Bean GraphQLResolver companyMaxSequence(ReplicationResolverFactory f, CompanyService s) {
 *     return f.maxSequence("companyMaxSequence", s);
 * }
 * }</pre>
 *
 * <p>The dispatch controller still verifies each field name against the SDL at boot,
 * so a typo in either the schema or the bean declaration fails fast as usual.
 */
@Component
public class ReplicationResolverFactory {

    private final FilterParser filterParser;
    private final GraphQLArgumentMapper argumentMapper;

    public ReplicationResolverFactory(FilterParser filterParser, GraphQLArgumentMapper argumentMapper) {
        this.filterParser = filterParser;
        this.argumentMapper = argumentMapper;
    }

    /** {@code <resources>(filter)} - filtered list of live resources. */
    public GraphQLResolver filteredList(String fieldName, ResourceService<?, ?> service) {
        return new FilteredListResolver(fieldName, service, filterParser);
    }

    /** {@code <resources>BySequence(sequence, bulkSize, filter)} - the replication feed. */
    public GraphQLResolver bySequence(String fieldName, ResourceService<?, ?> service) {
        return new BySequenceResolver(fieldName, service, filterParser, argumentMapper);
    }

    /** {@code count<Resources>ByFilter(filter, includeDeleted)} - count without fetching. */
    public GraphQLResolver countByFilter(String fieldName, ResourceService<?, ?> service) {
        return new CountByFilterResolver(fieldName, service, filterParser);
    }

    /** {@code <resource>MaxSequence} - the feed's current tail. */
    public GraphQLResolver maxSequence(String fieldName, ResourceService<?, ?> service) {
        return new MaxSequenceResolver(fieldName, service);
    }

    /** Common shape of the manufactured resolvers: always a Query field. */
    private abstract static class ReplicationQueryResolver implements GraphQLResolver {

        private final String fieldName;
        final ResourceService<?, ?> service;

        ReplicationQueryResolver(String fieldName, ResourceService<?, ?> service) {
            this.fieldName = fieldName;
            this.service = service;
        }

        @Override
        public final GraphQLOperationType operationType() {
            return GraphQLOperationType.QUERY;
        }

        @Override
        public final String fieldName() {
            return fieldName;
        }
    }

    private static final class FilteredListResolver extends ReplicationQueryResolver {

        private final FilterParser filterParser;

        FilteredListResolver(String fieldName, ResourceService<?, ?> service, FilterParser filterParser) {
            super(fieldName, service);
            this.filterParser = filterParser;
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) {
            return service.find(filterParser.parse(environment.getArgument("filter")));
        }
    }

    private static final class BySequenceResolver extends ReplicationQueryResolver {

        private final FilterParser filterParser;
        private final GraphQLArgumentMapper argumentMapper;

        BySequenceResolver(String fieldName, ResourceService<?, ?> service,
                           FilterParser filterParser, GraphQLArgumentMapper argumentMapper) {
            super(fieldName, service);
            this.filterParser = filterParser;
            this.argumentMapper = argumentMapper;
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) {
            long sequence = argumentMapper.longArgument(environment, "sequence");
            int bulkSize = argumentMapper.argument(environment, "bulkSize", Integer.class);
            FilterCriteria criteria = filterParser.parse(environment.getArgument("filter"));
            return service.getBySequence(sequence, bulkSize, criteria);
        }
    }

    private static final class CountByFilterResolver extends ReplicationQueryResolver {

        private final FilterParser filterParser;

        CountByFilterResolver(String fieldName, ResourceService<?, ?> service, FilterParser filterParser) {
            super(fieldName, service);
            this.filterParser = filterParser;
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) {
            FilterCriteria criteria = filterParser.parse(environment.getArgument("filter"));
            boolean includeDeleted = Boolean.TRUE.equals(environment.getArgument("includeDeleted"));
            return service.count(criteria, includeDeleted);
        }
    }

    private static final class MaxSequenceResolver extends ReplicationQueryResolver {

        MaxSequenceResolver(String fieldName, ResourceService<?, ?> service) {
            super(fieldName, service);
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) {
            return service.getMaxSequence();
        }
    }
}
