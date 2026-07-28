package com.example.infrastructure.replication;

import com.example.infrastructure.persistence.BaseEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * One page of a replication feed - what a {@code <resource>BySequence} query returns.
 *
 * <p>A fetched batch (the next {@code bulkSize} rows by sequence) partitions into
 * three disjoint parts:
 *
 * <ul>
 *   <li>{@link #getUpdated() updated} - live rows that match the client's filter (or
 *       every live row when no filter was sent): upsert these locally.</li>
 *   <li>{@link #getDeleted() deleted} - soft-deleted rows that match the filter:
 *       delete these locally.</li>
 *   <li>{@link #getFilteredOutIds() filteredOutIds} - ids of rows in the page that do
 *       NOT match the filter. A filtering client cannot tell whether such a row ever
 *       matched before, so it should drop any local copy - this is how a resource
 *       "leaving" the filter becomes visible without the client ever seeing the full
 *       row.</li>
 * </ul>
 *
 * <p>{@link #getNextSequence() nextSequence} is the resume point for the next poll:
 * the highest sequence in the page, or - when the page is empty because the client's
 * sequence is at (or beyond) the table's tail - the table's actual maximum sequence.
 * That "smart" snap-back keeps an over-shot client polling from a real position;
 * because the feed uses strictly-greater-than, re-sending the maximum always yields an
 * empty page until the next write.
 */
public final class ReplicationPage<T> {

    private final List<T> updated;
    private final List<T> deleted;
    private final List<Long> filteredOutIds;
    private final long nextSequence;

    public ReplicationPage(List<T> updated, List<T> deleted, List<Long> filteredOutIds, long nextSequence) {
        this.updated = Collections.unmodifiableList(updated);
        this.deleted = Collections.unmodifiableList(deleted);
        this.filteredOutIds = Collections.unmodifiableList(filteredOutIds);
        this.nextSequence = nextSequence;
    }

    /**
     * Partitions a fetched batch into a page: rows whose id is not in
     * {@code matchingIds} are reported by id only; matching rows land in
     * {@code updated} or {@code deleted} depending on their soft-delete flag, mapped
     * to views by {@code viewMapper}.
     */
    public static <E extends BaseEntity, V> ReplicationPage<V> partition(List<E> batch,
                                                                               Set<Long> matchingIds,
                                                                               long nextSequence,
                                                                               Function<E, V> viewMapper) {
        List<V> updated = new ArrayList<>();
        List<V> deleted = new ArrayList<>();
        List<Long> filteredOutIds = new ArrayList<>();
        for (E entity : batch) {
            if (!matchingIds.contains(entity.getId())) {
                filteredOutIds.add(entity.getId());
            } else if (entity.isDeleted()) {
                deleted.add(viewMapper.apply(entity));
            } else {
                updated.add(viewMapper.apply(entity));
            }
        }
        return new ReplicationPage<>(updated, deleted, filteredOutIds, nextSequence);
    }

    public List<T> getUpdated() {
        return updated;
    }

    public List<T> getDeleted() {
        return deleted;
    }

    public List<Long> getFilteredOutIds() {
        return filteredOutIds;
    }

    public long getNextSequence() {
        return nextSequence;
    }

    @Override
    public String toString() {
        return "ReplicationPage{updated=" + updated.size() + ", deleted=" + deleted.size()
                + ", filteredOut=" + filteredOutIds.size() + ", nextSequence=" + nextSequence + "}";
    }
}
