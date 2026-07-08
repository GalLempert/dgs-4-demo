package com.example.infrastructure.graphql.dispatch;

import graphql.schema.DataFetchingEnvironment;

/**
 * Contract for presenting a single field of any GraphQL type differently from its raw
 * value (e.g. {@code Person.gender} enriched from an enum catalog, or a date field
 * rendered in a client-chosen format).
 *
 * <p>Where {@link GraphQLResolver} handles root operations (queries/mutations), this
 * handles fields of object types. The source object backing the type is available via
 * {@link DataFetchingEnvironment#getSource()}.
 *
 * <p>Implementations are usually not hand-written: annotating a model class with
 * {@link com.example.infrastructure.graphql.model.GraphQLModel} and its fields with
 * {@code @GraphQLEnum} / {@code @GraphQLTemporal} generates them. Write one directly
 * only for bespoke per-field logic.
 */
public interface GraphQLFieldResolver {

    /** The GraphQL object type owning the field, e.g. {@code "Person"}. */
    String parentType();

    /** The field name on that type, e.g. {@code "gender"}. */
    String fieldName();

    Object resolve(DataFetchingEnvironment environment) throws Exception;
}
