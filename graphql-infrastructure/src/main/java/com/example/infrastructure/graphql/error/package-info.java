/**
 * The GraphQL-facing side of error handling: the global
 * {@link com.example.infrastructure.graphql.error.GraphQLExceptionHandler} catches
 * every exception thrown below the GraphQL layer and renders it as a structured
 * GraphQL error (literal, httpStatus, errorType, timestamp, details), delegating the
 * exception-to-ApiException translation to the
 * {@link com.example.infrastructure.error.mapping.ExceptionMapper} strategies.
 */
package com.example.infrastructure.graphql.error;
