package com.example.infrastructure.graphql.model;

import java.util.Collection;

/**
 * Domain modules expose one bean of this type listing their {@link GraphQLModel}
 * annotated classes, so the infrastructure can generate field resolvers from the
 * presentation annotations.
 */
@FunctionalInterface
public interface GraphQLModelSource {

    Collection<Class<?>> modelClasses();
}
