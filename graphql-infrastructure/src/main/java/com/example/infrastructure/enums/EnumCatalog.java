package com.example.infrastructure.enums;

import java.util.Optional;

/**
 * Source of human-readable data behind enum codes. Domain modules register beans of
 * this type (a static map, a database table, an external enum service...); the
 * {@code @GraphQLEnum} presentation asks each catalog in bean order and serves the
 * first match.
 */
public interface EnumCatalog {

    Optional<EnumEntry> entry(String catalogName, String code);
}
