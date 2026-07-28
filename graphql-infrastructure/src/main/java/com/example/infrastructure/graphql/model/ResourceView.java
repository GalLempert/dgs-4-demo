package com.example.infrastructure.graphql.model;

import java.time.LocalDateTime;

/**
 * Base view DTO carrying the technical fields every resource exposes - the GraphQL-side
 * mirror of {@link com.example.infrastructure.persistence.BaseEntity} and the backing
 * model of the {@code Resource} schema interface (declared in {@code common.graphqls}).
 * Domain views extend this (or {@code ReplicatedResourceView}) so id, audit timestamps
 * and the optimistic-locking version are declared once, in one hierarchy, and the
 * declarative mapper carries them from any {@code BaseEntity} subclass by name.
 */
public abstract class ResourceView {

    private Long id;
    private Long version;

    @GraphQLTemporal
    private LocalDateTime createdAt;

    @GraphQLTemporal
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
