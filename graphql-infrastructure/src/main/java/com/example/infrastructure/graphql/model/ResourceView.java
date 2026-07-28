package com.example.infrastructure.graphql.model;

import java.time.LocalDateTime;

/**
 * Base view DTO carrying the technical fields every resource exposes - the GraphQL-side
 * mirror of {@link com.example.infrastructure.persistence.BaseEntity} and the backing
 * model of the {@code Resource} schema interface (declared in {@code common.graphqls}).
 * Domain views extend this so id, audit timestamps, the optimistic-locking version and
 * the replication fields (sequence, deleted) are declared once, and the declarative
 * mapper carries them from any {@code BaseEntity} subclass by name.
 */
public abstract class ResourceView {

    private Long id;
    private Long version;

    @GraphQLTemporal
    private LocalDateTime createdAt;

    @GraphQLTemporal
    private LocalDateTime updatedAt;

    private Long sequence;
    private boolean deleted;

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

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
