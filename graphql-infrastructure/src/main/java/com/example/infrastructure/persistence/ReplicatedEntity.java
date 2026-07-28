package com.example.infrastructure.persistence;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

/**
 * Base class for entities exposed through the replication feed. On top of
 * {@link BaseEntity} it adds the two columns the feed protocol needs:
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
public abstract class ReplicatedEntity extends BaseEntity {

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private boolean deleted;

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
