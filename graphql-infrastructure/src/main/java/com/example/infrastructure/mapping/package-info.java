/**
 * Declarative object mapping between layer representations:
 * {@link com.example.infrastructure.mapping.DeclarativeMapper} copies same-shaped
 * objects (input DTO to entity, entity to view) by field name via Jackson, with
 * behavior declared through Jackson annotations on the classes instead of hand-written
 * mapping code. Simple fields flow through all layers with zero code; only fields that
 * differ between layers need explicit handling.
 */
package com.example.infrastructure.mapping;
