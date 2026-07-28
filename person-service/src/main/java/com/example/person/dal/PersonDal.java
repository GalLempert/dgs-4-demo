package com.example.person.dal;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterSpecificationBuilder;
import com.example.infrastructure.filter.QueryResultCap;
import com.example.infrastructure.replication.ReplicationSequences;
import com.example.person.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data access layer for the person domain. The only place that touches the repository;
 * the service layer depends on this class, never on Spring Data directly.
 *
 * <p>Filtered reads build their WHERE clause dynamically from the
 * {@link FilterCriteria} and are guarded by the {@link QueryResultCap}: the matching
 * rows are counted first and the query is rejected before fetching when the count
 * exceeds the cap.
 *
 * <p>Replication concerns are enforced here so no caller can forget them: every
 * {@link #save(Person)} stamps a fresh replication sequence on the row, and every
 * regular read excludes soft-deleted rows. Only the replication feed reads
 * ({@link #findBySequenceAfter}, {@link #matchingIds}) and the opt-in
 * {@link #count(FilterCriteria, boolean)} see deleted rows.
 */
@Component
public class PersonDal {

    private static final String RESOURCE_NAME = "Person";
    private static final String REPLICATION_SEQUENCE = "person_replication_seq";

    private static final Logger log = LoggerFactory.getLogger(PersonDal.class);

    private final PersonRepository personRepository;
    private final FilterSpecificationBuilder specificationBuilder;
    private final QueryResultCap queryResultCap;
    private final ReplicationSequences replicationSequences;

    public PersonDal(PersonRepository personRepository,
                     FilterSpecificationBuilder specificationBuilder,
                     QueryResultCap queryResultCap,
                     ReplicationSequences replicationSequences) {
        this.personRepository = personRepository;
        this.specificationBuilder = specificationBuilder;
        this.queryResultCap = queryResultCap;
        this.replicationSequences = replicationSequences;
    }

    /** Live (not soft-deleted) person by id. */
    public Optional<Person> findById(long id) {
        log.debug("DB: findById({})", id);
        return personRepository.findById(id).filter(person -> !person.isDeleted());
    }

    /** Filtered (or unfiltered, with {@link FilterCriteria#none()}) capped list query over live rows. */
    public List<Person> findAll(FilterCriteria criteria) {
        log.debug("DB: findAll({})", criteria);
        Specification<Person> specification = notDeleted().and(specificationBuilder.toSpecification(criteria));
        queryResultCap.enforce(RESOURCE_NAME, personRepository.count(specification));
        return personRepository.findAll(specification);
    }

    public List<Person> findByCity(String city) {
        log.debug("DB: findByCity('{}')", city);
        queryResultCap.enforce(RESOURCE_NAME, personRepository.countByAddressCityIgnoreCaseAndDeletedFalse(city));
        return personRepository.findByAddressCityIgnoreCaseAndDeletedFalse(city);
    }

    /**
     * Counts rows matching the filter; soft-deleted rows are excluded unless
     * {@code includeDeleted}. Count-only, so the result cap does not apply.
     */
    public long count(FilterCriteria criteria, boolean includeDeleted) {
        log.debug("DB: count({}, includeDeleted={})", criteria, includeDeleted);
        Specification<Person> specification = specificationBuilder.toSpecification(criteria);
        if (!includeDeleted) {
            specification = notDeleted().and(specification);
        }
        return personRepository.count(specification);
    }

    /**
     * Replication feed read: the next {@code bulkSize} rows (deleted ones included)
     * whose sequence is strictly greater than {@code sequence}, in sequence order.
     * The result cap doubles as the upper bound on {@code bulkSize} - a page larger
     * than the cap would materialize exactly the result set the cap exists to prevent.
     */
    public List<Person> findBySequenceAfter(long sequence, int bulkSize) {
        log.debug("DB: findBySequenceAfter({}, bulkSize={})", sequence, bulkSize);
        queryResultCap.enforce(RESOURCE_NAME, bulkSize);
        return personRepository.findBySequenceGreaterThanOrderBySequenceAsc(sequence, PageRequest.of(0, bulkSize));
    }

    /** Highest replication sequence in the person table, 0 when empty. */
    public long maxSequence() {
        log.debug("DB: maxSequence()");
        return personRepository.maxSequence();
    }

    /**
     * Of the given ids, the ones whose row matches the filter (deleted rows
     * included - the replication feed filters deleted resources too).
     */
    public Set<Long> matchingIds(List<Long> ids, FilterCriteria criteria) {
        log.debug("DB: matchingIds({} ids, {})", ids.size(), criteria);
        Specification<Person> specification = Specification
                .<Person>where((root, query, cb) -> root.get("id").in(ids))
                .and(specificationBuilder.toSpecification(criteria));
        return personRepository.findAll(specification).stream()
                .map(Person::getId)
                .collect(Collectors.toSet());
    }

    public boolean emailExists(String email) {
        log.debug("DB: emailExists('{}')", email);
        return personRepository.existsByEmailIgnoreCase(email);
    }

    /** Persists the person, stamping a fresh replication sequence on the row. */
    public Person save(Person person) {
        person.setSequence(replicationSequences.next(REPLICATION_SEQUENCE));
        log.debug("DB: save(person id={}, sequence={})", person.getId(), person.getSequence());
        return personRepository.save(person);
    }

    private Specification<Person> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }
}
