package com.example.infrastructure.graphql.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the model backing a GraphQL object type, enabling the
 * field-presentation annotations ({@link GraphQLEnum}, {@link GraphQLTemporal}) on its
 * fields. Register the class through a {@link GraphQLModelSource} bean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQLModel {

    /** The GraphQL type name this class backs, e.g. {@code "Person"}. */
    String value();
}
