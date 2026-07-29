/**
 * Cross-service resource references - the federation preparation.
 *
 * <p>Every domain runs as its own GraphQL service, so a resource in one service can
 * only refer to a resource owned by another service <em>by key</em>: the owning
 * service is the single source of truth for the resource's fields. A reference is
 * therefore an id-only stub, modeled by
 * {@link com.example.infrastructure.reference.ResourceRef}: the referencing service
 * stores the foreign ids, exposes a stub GraphQL type carrying just {@code id}
 * (named after the REAL type it points at, e.g. {@code type Person} inside the
 * company service's schema), and returns {@code ResourceRef} subclasses for it.
 *
 * <p>This shape is deliberately the Apollo Federation entity-reference pattern
 * without the machinery: a later PR turns the stub into a federated reference by
 * adding {@code @key(fields: "id")} to the owning type, {@code @extends}/
 * {@code @external} to the stub, and an entity fetcher in the owning service - the
 * gateway then resolves full objects across services while nothing about how
 * references are stored or exposed here changes. See {@code docs/FEDERATION.md}.
 */
package com.example.infrastructure.reference;
