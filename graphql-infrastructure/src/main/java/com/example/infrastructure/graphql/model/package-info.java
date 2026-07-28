/**
 * Annotation-driven field presentation: model classes declare with
 * {@link com.example.infrastructure.graphql.model.GraphQLModel},
 * {@link com.example.infrastructure.graphql.model.GraphQLEnum} and
 * {@link com.example.infrastructure.graphql.model.GraphQLTemporal} how the GraphQL
 * layer serializes their fields (enum enrichment, client-chosen time formats), and the
 * {@link com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory} turns
 * those declarations into field resolvers at startup (walking base classes, so shared
 * view hierarchies present their fields once). Reading the model tells you which
 * fields are presented as more than their raw value.
 *
 * <p>Also home of the technical-truth view base
 * {@link com.example.infrastructure.graphql.model.ResourceView} (id, version, audit
 * timestamps - the {@code Resource} schema interface's backing model) and the
 * {@link com.example.infrastructure.graphql.model.GraphQLModelTypeResolver}, which
 * resolves concrete types for interface-typed fields from the {@code @GraphQLModel}
 * annotation.
 */
package com.example.infrastructure.graphql.model;
