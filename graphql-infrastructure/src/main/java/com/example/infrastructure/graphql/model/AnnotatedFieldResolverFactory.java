package com.example.infrastructure.graphql.model;

import com.example.infrastructure.enums.EnumCatalog;
import com.example.infrastructure.enums.EnumEntry;
import com.example.infrastructure.graphql.dispatch.GraphQLFieldResolver;
import com.example.infrastructure.graphql.format.TemporalFormatterRegistry;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the presentation annotations on {@link GraphQLModel} classes into
 * {@link GraphQLFieldResolver}s, so declaring how a field is serialized is a one-line
 * annotation on the model instead of a hand-written resolver class.
 */
@Component
public class AnnotatedFieldResolverFactory {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedFieldResolverFactory.class);

    private final List<GraphQLModelSource> modelSources;
    private final List<EnumCatalog> enumCatalogs;
    private final TemporalFormatterRegistry temporalFormatters;

    public AnnotatedFieldResolverFactory(List<GraphQLModelSource> modelSources,
                                         List<EnumCatalog> enumCatalogs,
                                         TemporalFormatterRegistry temporalFormatters) {
        this.modelSources = modelSources;
        this.enumCatalogs = enumCatalogs;
        this.temporalFormatters = temporalFormatters;
    }

    public List<GraphQLFieldResolver> createResolvers() {
        List<GraphQLFieldResolver> resolvers = new ArrayList<>();
        for (GraphQLModelSource source : modelSources) {
            for (Class<?> modelClass : source.modelClasses()) {
                resolvers.addAll(resolversFor(modelClass));
            }
        }
        return resolvers;
    }

    private List<GraphQLFieldResolver> resolversFor(Class<?> modelClass) {
        GraphQLModel model = modelClass.getAnnotation(GraphQLModel.class);
        if (model == null) {
            throw new IllegalStateException(modelClass.getName()
                    + " is registered as a GraphQL model but is not annotated with @GraphQLModel");
        }
        List<GraphQLFieldResolver> resolvers = new ArrayList<>();
        // walk the whole hierarchy: technical fields (createdAt, updatedAt, ...) live
        // on shared view base classes and their annotations must apply to the subclass
        for (Class<?> level = modelClass; level != null && level != Object.class; level = level.getSuperclass()) {
            for (Field field : level.getDeclaredFields()) {
                createResolverFor(model.value(), field).ifPresent(resolvers::add);
            }
        }
        return resolvers;
    }

    private Optional<GraphQLFieldResolver> createResolverFor(String typeName, Field field) {
        GraphQLEnum enumAnnotation = field.getAnnotation(GraphQLEnum.class);
        GraphQLTemporal temporalAnnotation = field.getAnnotation(GraphQLTemporal.class);
        if (enumAnnotation != null && temporalAnnotation != null) {
            throw new IllegalStateException(typeName + "." + field.getName()
                    + " declares both @GraphQLEnum and @GraphQLTemporal - pick one");
        }
        if (enumAnnotation != null) {
            field.setAccessible(true);
            return Optional.of(new EnumEnrichmentResolver(typeName, field, enumAnnotation.value(), enumCatalogs));
        }
        if (temporalAnnotation != null) {
            if (!TemporalAccessor.class.isAssignableFrom(field.getType())) {
                throw new IllegalStateException(typeName + "." + field.getName()
                        + " is annotated @GraphQLTemporal but its type "
                        + field.getType().getSimpleName() + " is not a TemporalAccessor");
            }
            field.setAccessible(true);
            return Optional.of(new FormattedTemporalResolver(typeName, field, temporalFormatters));
        }
        return Optional.empty();
    }

    /** Serializes an enum-coded field as an {@code EnumValue} from the catalogs. */
    private static final class EnumEnrichmentResolver implements GraphQLFieldResolver {

        private final String parentType;
        private final Field field;
        private final String catalogName;
        private final List<EnumCatalog> catalogs;

        private EnumEnrichmentResolver(String parentType, Field field, String catalogName,
                                       List<EnumCatalog> catalogs) {
            this.parentType = parentType;
            this.field = field;
            this.catalogName = catalogName;
            this.catalogs = catalogs;
        }

        @Override
        public String parentType() {
            return parentType;
        }

        @Override
        public String fieldName() {
            return field.getName();
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) throws IllegalAccessException {
            Object value = field.get(environment.getSource());
            if (value == null) {
                return null;
            }
            String code = String.valueOf(value);
            return catalogs.stream()
                    .map(catalog -> catalog.entry(catalogName, code))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElseGet(() -> fallbackEntry(code));
        }

        private EnumEntry fallbackEntry(String code) {
            log.warn("No entry for code '{}' in enum catalog '{}' - serving the bare code", code, catalogName);
            return new EnumEntry(code, code, null);
        }
    }

    /** Renders a temporal field through the format chosen by the {@code format} argument. */
    private static final class FormattedTemporalResolver implements GraphQLFieldResolver {

        private static final String FORMAT_ARGUMENT = "format";
        private static final String DEFAULT_FORMAT = "ISO";

        private final String parentType;
        private final Field field;
        private final TemporalFormatterRegistry formatters;

        private FormattedTemporalResolver(String parentType, Field field, TemporalFormatterRegistry formatters) {
            this.parentType = parentType;
            this.field = field;
            this.formatters = formatters;
        }

        @Override
        public String parentType() {
            return parentType;
        }

        @Override
        public String fieldName() {
            return field.getName();
        }

        @Override
        public Object resolve(DataFetchingEnvironment environment) throws IllegalAccessException {
            Object value = field.get(environment.getSource());
            if (value == null) {
                return null;
            }
            String format = environment.getArgumentOrDefault(FORMAT_ARGUMENT, DEFAULT_FORMAT);
            return formatters.format(format, (TemporalAccessor) value);
        }
    }
}
