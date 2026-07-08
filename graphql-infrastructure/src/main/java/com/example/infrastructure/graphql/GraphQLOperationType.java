package com.example.infrastructure.graphql;

/**
 * The kind of GraphQL operation a {@link GraphQLResolver} handles, mapped to the
 * GraphQL root type it belongs to.
 */
public enum GraphQLOperationType {

    QUERY("Query"),
    MUTATION("Mutation");

    private final String parentTypeName;

    GraphQLOperationType(String parentTypeName) {
        this.parentTypeName = parentTypeName;
    }

    /** The name of the GraphQL root type ("Query" / "Mutation") this operation lives under. */
    public String parentTypeName() {
        return parentTypeName;
    }
}
