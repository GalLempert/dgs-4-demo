package com.example.infrastructure.persistence;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Version;
import java.time.LocalDateTime;

/**
 * Common JPA base class carrying the technical truth every resource has: surrogate id,
 * audit timestamps, an optimistic-locking version, and - because every resource is a
 * replicated resource - the replication sequence and soft-delete flag. Domain entities
 * in any module extend this instead of re-declaring the same boilerplate; the GraphQL
 * side mirrors it with the {@code Resource} schema interface and the
 * {@code ResourceView} DTO base.
 *
 * <ul>
 *   <li><b>sequence</b> - a per-table, monotonically increasing change number. The DAL
 *       assigns a fresh value (from a database sequence, see
 *       {@code ReplicationSequences}) on <em>every</em> write, so a row's sequence
 *       always reflects its latest change and clients can poll
 *       "everything after sequence X".</li>
 *   <li><b>deleted</b> - soft-delete marker. Deleting a resource flips this flag and
 *       bumps the sequence instead of removing the row, so replication clients see the
 *       deletion as a change; regular queries exclude deleted rows.</li>
 * </ul>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic-locking counter, incremented by JPA on every update. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private boolean deleted;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
