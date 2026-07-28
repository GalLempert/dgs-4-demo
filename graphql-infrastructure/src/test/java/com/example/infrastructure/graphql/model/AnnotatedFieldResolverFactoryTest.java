package com.example.infrastructure.graphql.model;

import com.example.infrastructure.enums.EnumCatalog;
import com.example.infrastructure.enums.EnumEntry;
import com.example.infrastructure.graphql.dispatch.GraphQLFieldResolver;
import com.example.infrastructure.graphql.format.IsoTemporalFormatter;
import com.example.infrastructure.graphql.format.Rfc1123TemporalFormatter;
import com.example.infrastructure.graphql.format.TemporalFormatterRegistry;
import com.example.infrastructure.graphql.format.UnixTemporalFormatter;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnotatedFieldResolverFactoryTest {

    @GraphQLModel("Sample")
    static class SampleModel {
        @GraphQLEnum("color")
        private String color;
        @GraphQLTemporal
        private LocalDate when;
        private String plain;

        SampleModel(String color, LocalDate when) {
            this.color = color;
            this.when = when;
        }
    }

    static class NotAnnotatedModel {
    }

    /** Technical base like ResourceView: annotated fields live on the superclass. */
    abstract static class TechnicalBaseModel {
        @GraphQLTemporal
        private LocalDate stamped;

        TechnicalBaseModel(LocalDate stamped) {
            this.stamped = stamped;
        }
    }

    @GraphQLModel("Derived")
    static class DerivedModel extends TechnicalBaseModel {
        private String own;

        DerivedModel(LocalDate stamped) {
            super(stamped);
        }
    }

    @GraphQLModel("Broken")
    static class TemporalOnStringModel {
        @GraphQLTemporal
        private String oops;
    }

    @GraphQLModel("Broken")
    static class DoublyAnnotatedModel {
        @GraphQLEnum("x")
        @GraphQLTemporal
        private LocalDate both;
    }

    private final EnumCatalog catalog = (name, code) ->
            "color".equals(name) && "RED".equals(code)
                    ? Optional.of(new EnumEntry("RED", "Red", "the color red"))
                    : Optional.empty();

    private final TemporalFormatterRegistry formatters = new TemporalFormatterRegistry(Arrays.asList(
            new IsoTemporalFormatter(), new UnixTemporalFormatter(), new Rfc1123TemporalFormatter()));

    private AnnotatedFieldResolverFactory factoryFor(Class<?>... modelClasses) {
        GraphQLModelSource source = () -> Arrays.asList(modelClasses);
        return new AnnotatedFieldResolverFactory(
                Collections.singletonList(source), Collections.singletonList(catalog), formatters);
    }

    private DataFetchingEnvironment envWithSource(Object source) {
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getSource()).thenReturn(source);
        when(environment.getArgumentOrDefault("format", "ISO")).thenReturn("ISO");
        return environment;
    }

    @Test
    void createsOneResolverPerAnnotatedFieldOnly() {
        List<GraphQLFieldResolver> resolvers = factoryFor(SampleModel.class).createResolvers();

        assertThat(resolvers).hasSize(2);
        assertThat(resolvers).allMatch(resolver -> resolver.parentType().equals("Sample"));
        assertThat(resolvers.stream().map(GraphQLFieldResolver::fieldName).collect(Collectors.toList()))
                .containsExactlyInAnyOrder("color", "when");
    }

    @Test
    void annotatedFieldsInheritedFromABaseClassResolveForTheSubclassType() throws Exception {
        List<GraphQLFieldResolver> resolvers = factoryFor(DerivedModel.class).createResolvers();

        assertThat(resolvers).hasSize(1);
        GraphQLFieldResolver resolver = resolvers.get(0);
        assertThat(resolver.parentType()).isEqualTo("Derived");
        assertThat(resolver.fieldName()).isEqualTo("stamped");
        assertThat(resolver.resolve(envWithSource(new DerivedModel(LocalDate.of(1985, 12, 10)))))
                .isEqualTo("1985-12-10");
    }

    @Test
    void enumFieldIsEnrichedFromTheCatalog() throws Exception {
        GraphQLFieldResolver resolver = resolverFor("color");

        Object result = resolver.resolve(envWithSource(new SampleModel("RED", null)));

        EnumEntry entry = (EnumEntry) result;
        assertThat(entry.getCode()).isEqualTo("RED");
        assertThat(entry.getLabel()).isEqualTo("Red");
        assertThat(entry.getDescription()).isEqualTo("the color red");
    }

    @Test
    void unknownEnumCodeFallsBackToBareCode() throws Exception {
        GraphQLFieldResolver resolver = resolverFor("color");

        EnumEntry entry = (EnumEntry) resolver.resolve(envWithSource(new SampleModel("BLUE", null)));

        assertThat(entry.getCode()).isEqualTo("BLUE");
        assertThat(entry.getLabel()).isEqualTo("BLUE");
        assertThat(entry.getDescription()).isNull();
    }

    @Test
    void nullEnumValueStaysNull() throws Exception {
        assertThat(resolverFor("color").resolve(envWithSource(new SampleModel(null, null)))).isNull();
    }

    @Test
    void temporalFieldUsesDefaultIsoFormat() throws Exception {
        Object result = resolverFor("when")
                .resolve(envWithSource(new SampleModel(null, LocalDate.of(1985, 12, 10))));

        assertThat(result).isEqualTo("1985-12-10");
    }

    @Test
    void temporalFieldHonorsRequestedFormat() throws Exception {
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getSource()).thenReturn(new SampleModel(null, LocalDate.of(1985, 12, 10)));
        when(environment.getArgumentOrDefault("format", "ISO")).thenReturn("UNIX");

        assertThat(resolverFor("when").resolve(environment)).isEqualTo("503020800");
    }

    @Test
    void nullTemporalValueStaysNull() throws Exception {
        assertThat(resolverFor("when").resolve(envWithSource(new SampleModel(null, null)))).isNull();
    }

    @Test
    void registeredClassWithoutModelAnnotationFailsFast() {
        assertThatThrownBy(() -> factoryFor(NotAnnotatedModel.class).createResolvers())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@GraphQLModel");
    }

    @Test
    void temporalAnnotationOnNonTemporalFieldFailsFast() {
        assertThatThrownBy(() -> factoryFor(TemporalOnStringModel.class).createResolvers())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TemporalAccessor");
    }

    @Test
    void bothAnnotationsOnOneFieldFailFast() {
        assertThatThrownBy(() -> factoryFor(DoublyAnnotatedModel.class).createResolvers())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pick one");
    }

    private GraphQLFieldResolver resolverFor(String fieldName) {
        return factoryFor(SampleModel.class).createResolvers().stream()
                .filter(resolver -> resolver.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no resolver for " + fieldName));
    }
}
