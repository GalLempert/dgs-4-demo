/**
 * Common JPA building blocks:
 * {@link com.example.infrastructure.persistence.BaseEntity} with surrogate id and
 * audit timestamps, and {@link com.example.infrastructure.persistence.ReplicatedEntity}
 * adding the replication sequence and soft-delete flag for entities exposed through a
 * replication feed.
 */
package com.example.infrastructure.persistence;
