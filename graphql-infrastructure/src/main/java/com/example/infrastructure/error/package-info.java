/**
 * The structured error model shared by all layers:
 * {@link com.example.infrastructure.error.ApiException} (base of every known failure),
 * the {@link com.example.infrastructure.error.ErrorCode} catalog (literal + HTTP status
 * + GraphQL classification) and per-field
 * {@link com.example.infrastructure.error.ErrorDetail}s.
 *
 * <p>Services throw these framework-neutral exceptions and never see GraphQL types;
 * the {@code graphql.error} package renders them.
 */
package com.example.infrastructure.error;
