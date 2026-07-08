/**
 * Typed access to raw GraphQL arguments: converts argument maps into DTOs and scalars
 * into primitives, turning malformed input into INVALID_ARGUMENT (400) errors instead
 * of leaked parse exceptions.
 */
package com.example.infrastructure.graphql.arguments;
