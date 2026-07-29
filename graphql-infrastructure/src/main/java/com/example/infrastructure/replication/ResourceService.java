package com.example.infrastructure.replication;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.mapping.DeclarativeMapper;
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
 * four standard queries (filtered list, replication feed, count, max sequence) and the
 * five standard mutations (save-new, update-by-filter, save-or-update, save-or-override,
 * delete-by-filter) plus the id-based soft delete. A domain service extends this class,
 * supplies the entity-to-view mapping, and only adds genuinely domain-specific
 * operations:
 *
 * <pre>{@code
 * @Service
 * public class CompanyService extends ResourceService<Company, CompanyView> {
 *     public CompanyService(CompanyDal dal, DeclarativeMapper mapper) {
 *         super(dal, mapper, Company.class);
 *     }
 *     protected CompanyView toView(Company company) { ... }
 * }
 * }</pre>
 *
 * <p>Every write mutation runs the same pipeline - apply the input to the entity
 * (declaratively, by field name), {@link #validate}, {@link #calculateDerivedFields},
 * save through the DAL (which stamps the replication sequence) - and the filtered
 * mutations reuse the exact same {@link FilterCriteria} machinery as the queries.
 * The pipeline steps are protected hooks with sensible defaults, so a domain overrides
 * only what it genuinely specializes (business validation, stored derived values,
 * the natural key, collection back-references).
 *
 * <p>All read methods run in read-only transactions so lazy collections can be
 * materialized during view mapping (the subclass must be a Spring bean for the
 * transaction proxy to apply).
 */
public abstract class ResourceService<E extends BaseEntity, V> {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceDal<E> replicatedDal;
    private final DeclarativeMapper declarativeMapper;
    private final Class<E> entityType;

    protected ResourceService(ResourceDal<E> replicatedDal, DeclarativeMapper declarativeMapper,
                              Class<E> entityType) {
        this.replicatedDal = replicatedDal;
        this.declarativeMapper = declarativeMapper;
        this.entityType = entityType;
    }

    /** Maps a fetched entity to the view the GraphQL layer exposes. */
    protected abstract V toView(E entity);

    /** The declarative mapper, for subclass mapping code (typically entity-to-view). */
    protected DeclarativeMapper declarativeMapper() {
        return declarativeMapper;
    }

    // ------------------------------------------------------------------- reads

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
        // Smart resume point: highest sequence in the page. On an empty page, snap an
        // over-shot cursor DOWN to the table's maximum but never advance it - the max
        // is read after the page query, so a concurrent commit in between could have a
        // higher sequence whose row this page did not return; advancing to it would
        // make the client's strictly-greater-than poll skip that row forever.
        long nextSequence = batch.isEmpty()
                ? Math.min(sequence, replicatedDal.maxSequence())
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

    // --------------------------------------------------------------- mutations

    /**
     * Save-new: builds a fresh entity from the input, runs the write pipeline
     * (validate, calculate derived fields) and persists it.
     */
    @Transactional
    public V saveNew(Object input) {
        E entity = prepareForSave(toEntity(input));
        V view = toView(replicatedDal.save(entity));
        log.info("Created new {} {}", replicatedDal.resourceName(), entity.getId());
        return view;
    }

    /**
     * Update-by-filter: fetches every live row matching the criteria, merges the
     * input's non-null fields onto each (declaratively, via the object mapper), runs
     * the write pipeline per row and saves the whole list in one bulk write. The
     * filter must be non-empty - an unfiltered update of the entire table is almost
     * certainly a client mistake and is rejected.
     */
    @Transactional
    public List<V> update(FilterCriteria criteria, Object input) {
        requireExplicitFilter(criteria, "update");
        List<E> entities = replicatedDal.findAll(criteria);
        entities.forEach(entity -> prepareForSave(mergeIntoEntity(input, entity)));
        List<V> views = toViews(replicatedDal.saveAll(entities));
        log.info("Updated {} {} row(s) matching {}", views.size(), replicatedDal.resourceName(), criteria);
        return views;
    }

    /**
     * Upsert, orchestration only: when no live row matches the criteria the input is
     * handed to {@link #saveNew}; otherwise the matching rows are handed to
     * {@link #update} with {@code updateInput} (falling back to {@code input} when the
     * caller did not provide a separate update payload). No create or update logic of
     * its own - the decision is this method's entire job.
     */
    @Transactional
    public List<V> saveOrUpdate(FilterCriteria criteria, Object input, Object updateInput) {
        requireExplicitFilter(criteria, "saveOrUpdate");
        if (count(criteria, false) == 0) {
            log.debug("saveOrUpdate: no {} matches {} - creating", replicatedDal.resourceName(), criteria);
            return Collections.singletonList(saveNew(input));
        }
        log.debug("saveOrUpdate: {} matches exist for {} - updating", replicatedDal.resourceName(), criteria);
        return update(criteria, updateInput != null ? updateInput : input);
    }

    /**
     * Create-or-replace by natural key: looks the input's {@link #naturalKeyOf natural
     * key} up; a miss delegates to {@link #saveNew}, a hit overrides EVERYTHING the
     * input declares on the existing row (nulls included - the row's business state
     * becomes exactly the input; technical fields id/version/timestamps/sequence stay
     * untouched). More than one match means the domain's natural key is not actually
     * unique - a server-side wiring fault.
     */
    @Transactional
    public V saveOrOverride(Object input) {
        FilterCriteria naturalKey = naturalKeyOf(input);
        requireExplicitFilter(naturalKey, "saveOrOverride");
        List<E> matches = replicatedDal.findAll(naturalKey);
        if (matches.isEmpty()) {
            log.debug("saveOrOverride: no {} matches {} - creating", replicatedDal.resourceName(), naturalKey);
            return saveNew(input);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(replicatedDal.resourceName() + " natural key " + naturalKey
                    + " matches " + matches.size() + " rows - naturalKeyOf() must identify at most one");
        }
        E entity = prepareForSave(overrideEntity(input, matches.get(0)));
        V view = toView(replicatedDal.save(entity));
        log.info("Overrode {} {} matching {}", replicatedDal.resourceName(), entity.getId(), naturalKey);
        return view;
    }

    /**
     * Delete-by-filter: soft-deletes every live row matching the criteria in one bulk
     * write (each flipped row gets a fresh sequence, so the deletions travel through
     * the replication feed) and reports how many rows were affected. Like
     * {@link #update}, an empty filter is rejected.
     */
    @Transactional
    public int deleteByFilter(FilterCriteria criteria) {
        requireExplicitFilter(criteria, "delete");
        List<E> entities = replicatedDal.findAll(criteria);
        entities.forEach(entity -> entity.setDeleted(true));
        replicatedDal.saveAll(entities);
        log.info("Soft-deleted {} {} row(s) matching {}", entities.size(), replicatedDal.resourceName(), criteria);
        return entities.size();
    }

    /**
     * Soft delete by id: flips the deleted flag and (via the DAL save) bumps the
     * replication sequence, so the deletion travels through the replication feed. The
     * row stays in the table but disappears from all regular queries.
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

    // ------------------------------------------------- mutation pipeline hooks

    /**
     * How a create input becomes a fresh entity. Default: declarative field-by-field
     * mapping - a same-shaped input needs no code. Override for genuinely different
     * construction.
     */
    protected E toEntity(Object input) {
        return declarativeMapper.map(input, entityType);
    }

    /**
     * How an update input lands on an existing entity: non-null input fields replace
     * the entity's values, everything else stays. Override to post-process what the
     * declarative merge cannot know (e.g. re-wiring child back-references).
     */
    protected E mergeIntoEntity(Object input, E entity) {
        return declarativeMapper.merge(input, entity);
    }

    /**
     * How an override input lands on an existing entity: every field the input
     * declares replaces the entity's value, nulls included. Same override use-case as
     * {@link #mergeIntoEntity}.
     */
    protected E overrideEntity(Object input, E entity) {
        return declarativeMapper.override(input, entity);
    }

    /**
     * Business validation, run on every mutation after the input has been applied and
     * before the save. Throw an {@link ApiException} to reject. Default: accept.
     */
    protected void validate(E entity) {
        // no domain rules by default
    }

    /**
     * Recomputes values the domain stores rather than derives at read time, run right
     * before every save. Default: nothing is stored derived.
     */
    protected void calculateDerivedFields(E entity) {
        // nothing stored derived by default
    }

    /**
     * The filter identifying the at-most-one existing row an input refers to - what
     * makes {@link #saveOrOverride} able to decide create-vs-override on its own.
     * A domain that wires the save-or-override mutation must override this
     * (typically one {@link FilterCriteria#whereEquals} on its natural key).
     */
    protected FilterCriteria naturalKeyOf(Object input) {
        throw new IllegalStateException(replicatedDal.resourceName()
                + " wires saveOrOverride but does not override naturalKeyOf() - "
                + "the service must declare which input field(s) identify an existing row");
    }

    // ---------------------------------------------------------------- internals

    private E prepareForSave(E entity) {
        validate(entity);
        calculateDerivedFields(entity);
        return entity;
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

    private void requireExplicitFilter(FilterCriteria criteria, String operation) {
        if (criteria == null || criteria.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "A non-empty filter is required to " + operation + " " + replicatedDal.resourceName()
                            + " rows - an empty filter would affect the entire table",
                    Collections.singletonList(new ErrorDetail("filter", "required",
                            "filter must contain at least one field predicate")));
        }
    }
}
