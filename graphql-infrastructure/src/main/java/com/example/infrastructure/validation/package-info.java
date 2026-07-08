/**
 * Server-side JSON Schema validation - constraints the GraphQL type system cannot
 * express (value ranges, patterns, array sizes, formats). Schemas live under
 * {@code classpath:json-schema/<name>.json}; violations surface as
 * {@link com.example.infrastructure.validation.SchemaValidationException} with one
 * detail per broken constraint.
 */
package com.example.infrastructure.validation;
