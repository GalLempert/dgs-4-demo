package com.example.infrastructure.replication;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.persistence.BaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base service for a replicated resource: the complete service-layer behavior of the
 * four standard queries (filtered list, replication feed, count, max sequence) plus
 * the soft delete. A domain service extends this class, supplies the entity-to-view
 * mapping, and only adds genuinely domain-specific operations:
 *
 * <pre>{@code
 * @Service
 * public class CompanyService extends ResourceService<Company, CompanyView> {
 *     public CompanyService(CompanyDal dal, DeclarativeMapper mapper) { ... }
 *     protected CompanyView toView(Company company) { ... }
 * }
 * }</pre>
 *
 * <p>All read methods run in read-only transactions so lazy collections can be
 * materialized during view mapping (the subclass must be a Spring bean for the
 * transaction proxy to apply).
 */
public abstract class ResourceService<E extends BaseEntity, V> {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceDal<E> replicatedDal;

    protected ResourceService(ResourceDal<E> replicatedDal) {
        this.replicatedDal = replicatedDal;
    }

    /** Maps a fetched entity to the view the GraphQL layer exposes. */
    protected abstract V toView(E entity);

    /** Filtered (or unfiltered) list of live resources, mapped to views. */
    @Transactional(readOnly = true)
    public List<V> find(FilterCriteria criteria) {
        List<V> views = toViews(replicatedDal.findAll(criteria));
        log.debug("Fetched {} {} rows for {}", views.size(), replicatedDal.resourceName(), criteria);
        return views;
    }

    /**
     * One page of the replication feed: the next {@code bulkSize} rows whose sequence
     * is strictly greater than {@code sequence}, partitioned into updated / deleted /
     * filtered-out. See {@link ReplicationPage} for the protocol contract.
     */
    @Transactional(readOnly = true)
    public ReplicationPage<V> getBySequence(long sequence, int bulkSize, FilterCriteria criteria) {
        requirePositiveBulkSize(bulkSize);
        List<E> batch = replicatedDal.findBySequenceAfter(sequence, bulkSize);
        // Smart resume point: highest sequence in the page; on an empty page (client
        // caught up or over-shot) snap back to the table's actual maximum.
        long nextSequence = batch.isEmpty()
                ? replicatedDal.maxSequence()
                : batch.get(batch.size() - 1).getSequence();
        ReplicationPage<V> page =
                ReplicationPage.partition(batch, matchingIds(batch, criteria), nextSequence, this::toView);
        log.debug("{} replication page after sequence {}: {}", replicatedDal.resourceName(), sequence, page);
        return page;
    }

    /** Counts matching resources; soft-deleted rows are excluded unless {@code includeDeleted}. */
    @Transactional(readOnly = true)
    public long count(FilterCriteria criteria, boolean includeDeleted) {
        long count = replicatedDal.count(criteria, includeDeleted);
        log.debug("Counted {} {} rows for {} (includeDeleted={})",
                count, replicatedDal.resourceName(), criteria, includeDeleted);
        return count;
    }

    /** The current tail of the replication feed (0 on an empty table). */
    @Transactional(readOnly = true)
    public long getMaxSequence() {
        return replicatedDal.maxSequence();
    }

    /**
     * Soft delete: flips the deleted flag and (via the DAL save) bumps the replication
     * sequence, so the deletion travels through the replication feed. The row stays in
     * the table but disappears from all regular queries.
     */
    @Transactional
    public boolean softDelete(long id) {
        Optional<E> entity = replicatedDal.findById(id);
        if (!entity.isPresent()) {
            log.info("Delete requested for {} {} but it does not exist (or is already deleted)",
                    replicatedDal.resourceName(), id);
            return false;
        }
        E row = entity.get();
        row.setDeleted(true);
        replicatedDal.save(row);
        log.info("Soft-deleted {} {}", replicatedDal.resourceName(), id);
        return true;
    }

    protected List<V> toViews(List<E> entities) {
        return entities.stream().map(this::toView).collect(Collectors.toList());
    }

    /**
     * Which rows of the batch match the client's filter. Without a filter every row
     * matches; with one, the DAL re-checks the batch's ids against the same
     * dynamically built WHERE clause used everywhere else.
     */
    private Set<Long> matchingIds(List<E> batch, FilterCriteria criteria) {
        List<Long> ids = batch.stream().map(BaseEntity::getId).collect(Collectors.toList());
        if (batch.isEmpty() || criteria.isEmpty()) {
            return new HashSet<>(ids);
        }
        return replicatedDal.matchingIds(ids, criteria);
    }

    private void requirePositiveBulkSize(int bulkSize) {
        if (bulkSize < 1) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "Argument 'bulkSize' must be at least 1 but was " + bulkSize,
                    Collections.singletonList(new ErrorDetail("bulkSize", "min", "bulkSize must be >= 1")));
        }
    }
}
