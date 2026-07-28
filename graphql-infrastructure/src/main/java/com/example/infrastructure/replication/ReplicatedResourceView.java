package com.example.infrastructure.replication;

import com.example.infrastructure.graphql.model.ResourceView;

/**
 * Base view DTO of a replicated resource - the GraphQL-side mirror of
 * {@link com.example.infrastructure.persistence.ReplicatedEntity} and the backing
 * model of the {@code ReplicatedResource} schema interface: the technical fields of
 * {@link ResourceView} plus the replication sequence and the soft-delete marker.
 */
public abstract class ReplicatedResourceView extends ResourceView {

    private Long sequence;
    private boolean deleted;

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
