/**
 * Common JPA building blocks:
 * {@link com.example.infrastructure.persistence.BaseEntity} carries the technical
 * truth every resource has - surrogate id, audit timestamps, optimistic-locking
 * version, and (since every resource is a replicated resource) the replication
 * sequence and soft-delete flag.
 */
package com.example.infrastructure.persistence;
