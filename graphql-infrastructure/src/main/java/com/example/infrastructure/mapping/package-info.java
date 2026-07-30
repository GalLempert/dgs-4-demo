/**
 * Declarative object mapping between layer representations:
 * {@link com.example.infrastructure.mapping.DeclarativeMapper} copies same-shaped
 * objects (input DTO to entity, entity to view) by field name via Jackson, with
 * behavior declared through Jackson annotations on the classes instead of hand-written
 * mapping code. Simple fields flow through all layers with zero code; only fields that
 * differ between layers need explicit handling.
 *
 * <p>Three operations back the three write paths: {@code map} builds a new object
 * (create), {@code merge} copies only non-null source fields onto an existing target
 * (partial update), {@code override} copies every declared source field, nulls
 * included (full replace). The in-place variants preserve the identity of
 * JPA-managed collections.
 */
package com.example.infrastructure.mapping;
