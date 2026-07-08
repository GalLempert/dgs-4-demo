/**
 * Enum enrichment: catalogs map stored enum codes to human-readable entries
 * ({@code code} + {@code label} + {@code description}). Fields annotated with
 * {@code @GraphQLEnum} are served from here; swapping a static catalog for an external
 * enum service is a new {@link com.example.infrastructure.enums.EnumCatalog} bean,
 * nothing else.
 */
package com.example.infrastructure.enums;
