/**
 * Annotation-driven field presentation: model classes declare with
 * {@link com.example.infrastructure.graphql.model.GraphQLModel},
 * {@link com.example.infrastructure.graphql.model.GraphQLEnum} and
 * {@link com.example.infrastructure.graphql.model.GraphQLTemporal} how the GraphQL
 * layer serializes their fields (enum enrichment, client-chosen time formats), and the
 * {@link com.example.infrastructure.graphql.model.AnnotatedFieldResolverFactory} turns
 * those declarations into field resolvers at startup. Reading the model tells you
 * which fields are presented as more than their raw value.
 */
package com.example.infrastructure.graphql.model;
