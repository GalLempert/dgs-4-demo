package com.example.infrastructure.graphql.model;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsTypeResolver;

/**
 * Resolves the concrete GraphQL type of a value returned where the schema declares the
 * technical {@code Resource} interface. The concrete type is whatever the view class
 * declares in its {@link GraphQLModel} annotation - the same source of truth the
 * field-presentation machinery uses - so view class names never need to match GraphQL
 * type names.
 */
@DgsComponent
public class GraphQLModelTypeResolver {

    @DgsTypeResolver(name = "Resource")
    public String resolveResource(Object view) {
        GraphQLModel model = view.getClass().getAnnotation(GraphQLModel.class);
        if (model == null) {
            throw new IllegalStateException(view.getClass().getName()
                    + " was returned for a Resource-typed field but is not annotated with @GraphQLModel");
        }
        return model.value();
    }
}
