package com.example.infrastructure.replication;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.QueryResultCap;
import com.example.infrastructure.persistence.BaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base data access layer for a replicated resource. Everything the replication
 * protocol and the standard filtered reads need is implemented here once; a domain
 * DAL extends this class, names its resource and its database sequence, and only adds
 * genuinely domain-specific queries:
 *
 * <pre>{@code
 * @Component
 * public class CompanyDal extends ResourceDal<Company> {
 *     public CompanyDal(CompanyRepository repository, ResourceDalSupport support) {
 *         super("Company", "company_replication_seq", repository, support);
 *     }
 * }
 * }</pre>
 *
 * <p>The replication invariants are enforced here so no caller can forget them: every
 * {@link #save} stamps a fresh replication sequence on the row, and every regular read
 * excludes soft-deleted rows. Only the feed reads ({@link #findBySequenceAfter},
 * {@link #matchingIds}) and the opt-in {@link #count(FilterCriteria, boolean)} see
 * deleted rows. Filtered reads are guarded by the {@link QueryResultCap}: matching
 * rows are counted first and the query is rejected before fetching when over the cap.
 */
public abstract class ResourceDal<E extends BaseEntity> {

    private static final Logger log = LoggerFactory.getLogger(ResourceDal.class);

    private final String resourceName;
    private final String sequenceName;
    private final ResourceRepository<E> repository;
    private final ResourceDalSupport support;

    protected ResourceDal(String resourceName,
                            String sequenceName,
                            ResourceRepository<E> repository,
                            ResourceDalSupport support) {
        this.resourceName = resourceName;
        this.sequenceName = sequenceName;
        this.repository = repository;
        this.support = support;
        // startup-time registration: creates the DB sequence + write-lock row here,
        // outside any business transaction (DDL would implicitly commit one)
        support.replicationSequences().register(sequenceName);
    }

    /** Live (not soft-deleted) row by id. */
    public Optional<E> findById(long id) {
        log.debug("DB[{}]: findById({})", resourceName, id);
        return repository.findById(id).filter(entity -> !entity.isDeleted());
    }

    /** Filtered (or unfiltered, with {@link FilterCriteria#none()}) capped list query over live rows. */
    public List<E> findAll(FilterCriteria criteria) {
        log.debug("DB[{}]: findAll({})", resourceName, criteria);
        Specification<E> specification = notDeleted().and(support.specificationBuilder().toSpecification(criteria));
        support.queryResultCap().enforce(resourceName, repository.count(specification));
        return repository.findAll(specification);
    }

    /**
     * Counts rows matching the filter; soft-deleted rows are excluded unless
     * {@code includeDeleted}. Count-only, so the result cap does not apply.
     */
    public long count(FilterCriteria criteria, boolean includeDeleted) {
        log.debug("DB[{}]: count({}, includeDeleted={})", resourceName, criteria, includeDeleted);
        Specification<E> specification = support.specificationBuilder().toSpecification(criteria);
        if (!includeDeleted) {
            specification = notDeleted().and(specification);
        }
        return repository.count(specification);
    }

    /**
     * Replication feed read: the next {@code bulkSize} rows (deleted ones included)
     * whose sequence is strictly greater than {@code sequence}, in sequence order.
     * The result cap doubles as the upper bound on {@code bulkSize} - a page larger
     * than the cap would materialize exactly the result set the cap exists to prevent.
     */
    public List<E> findBySequenceAfter(long sequence, int bulkSize) {
        log.debug("DB[{}]: findBySequenceAfter({}, bulkSize={})", resourceName, sequence, bulkSize);
        support.queryResultCap().enforce(resourceName, bulkSize);
        return repository.findBySequenceGreaterThanOrderBySequenceAsc(sequence, PageRequest.of(0, bulkSize));
    }

    /** Highest replication sequence in the resource table, 0 when empty. */
    public long maxSequence() {
        log.debug("DB[{}]: maxSequence()", resourceName);
        return repository.maxSequence();
    }

    /**
     * Of the given ids, the ones whose row matches the filter (deleted rows
     * included - the replication feed filters deleted resources too).
     */
    public Set<Long> matchingIds(List<Long> ids, FilterCriteria criteria) {
        log.debug("DB[{}]: matchingIds({} ids, {})", resourceName, ids.size(), criteria);
        Specification<E> specification = Specification
                .<E>where((root, query, cb) -> root.get("id").in(ids))
                .and(support.specificationBuilder().toSpecification(criteria));
        return repository.findAll(specification).stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Persists the row, stamping a fresh replication sequence on it. Allocation is
     * lock-free by deliberate choice: under concurrent writers of the same table,
     * sequence order can diverge from commit order (see
     * {@link ReplicationSequences} for the documented anomaly and
     * {@link ReplicationOutbox} for the planned commit-ordered replacement). The save
     * is flushed immediately so database-managed values ({@code @Version},
     * {@code @PreUpdate} timestamps) are current on the returned entity - mutation
     * responses report the committed state.
     */
    public E save(E entity) {
        entity.setSequence(support.replicationSequences().next(sequenceName));
        log.debug("DB[{}]: save(id={}, sequence={})", resourceName, entity.getId(), entity.getSequence());
        return repository.saveAndFlush(entity);
    }

    /** The resource name used in error messages and logs, e.g. {@code "Person"}. */
    public String resourceName() {
        return resourceName;
    }

    /** For domain-specific capped queries in subclasses. */
    protected QueryResultCap resultCap() {
        return support.queryResultCap();
    }

    /** Live-rows-only predicate for domain-specific specifications in subclasses. */
    protected Specification<E> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }
}
