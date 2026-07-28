package com.example.person.service;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.DuplicateResourceException;
import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.error.ErrorDetail;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.replication.ReplicationPage;
import com.example.person.dal.PersonDal;
import com.example.person.domain.Person;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business layer of the person domain: orchestrates the DAL, enforces business rules
 * (e.g. email uniqueness) and returns {@link PersonView}s enriched by
 * {@link PersonCalculations} through the {@link PersonMapper}.
 */
@Service
public class PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonService.class);

    private final PersonDal personDal;
    private final PersonMapper personMapper;

    public PersonService(PersonDal personDal, PersonMapper personMapper) {
        this.personDal = personDal;
        this.personMapper = personMapper;
    }

    @Transactional(readOnly = true)
    public PersonView getPerson(long id) {
        log.debug("Fetching person {}", id);
        return personMapper.toView(requirePerson(id));
    }

    @Transactional(readOnly = true)
    public List<PersonView> getAllPersons() {
        return findPersons(FilterCriteria.none());
    }

    @Transactional(readOnly = true)
    public List<PersonView> findPersons(FilterCriteria criteria) {
        List<PersonView> views = toViews(personDal.findAll(criteria));
        log.debug("Fetched {} persons for {}", views.size(), criteria);
        return views;
    }

    /**
     * One page of the replication feed: the next {@code bulkSize} rows whose sequence
     * is strictly greater than {@code sequence}, partitioned into updated / deleted /
     * filtered-out. See {@link ReplicationPage} for the protocol contract.
     */
    @Transactional(readOnly = true)
    public ReplicationPage<PersonView> getPersonsBySequence(long sequence, int bulkSize, FilterCriteria criteria) {
        requirePositiveBulkSize(bulkSize);
        List<Person> batch = personDal.findBySequenceAfter(sequence, bulkSize);
        // Smart resume point: highest sequence in the page; on an empty page (client
        // caught up or over-shot) snap back to the table's actual maximum.
        long nextSequence = batch.isEmpty()
                ? personDal.maxSequence()
                : batch.get(batch.size() - 1).getSequence();
        ReplicationPage<PersonView> page =
                ReplicationPage.partition(batch, matchingIds(batch, criteria), nextSequence, personMapper::toView);
        log.debug("Replication page after sequence {}: {}", sequence, page);
        return page;
    }

    @Transactional(readOnly = true)
    public long countPersons(FilterCriteria criteria, boolean includeDeleted) {
        long count = personDal.count(criteria, includeDeleted);
        log.debug("Counted {} persons for {} (includeDeleted={})", count, criteria, includeDeleted);
        return count;
    }

    @Transactional(readOnly = true)
    public long getMaxSequence() {
        return personDal.maxSequence();
    }

    @Transactional(readOnly = true)
    public List<PersonView> getPersonsByCity(String city) {
        List<PersonView> views = toViews(personDal.findByCity(city));
        log.debug("Fetched {} persons in city '{}'", views.size(), city);
        return views;
    }

    @Transactional
    public PersonView createPerson(CreatePersonInput input) {
        log.info("Creating person with email {}", input.getEmail());
        requireUniqueEmail(input.getEmail());
        PersonView view = personMapper.toView(personDal.save(personMapper.toEntity(input)));
        log.info("Created person {} ({})", view.getId(), view.getFullName());
        return view;
    }

    @Transactional
    public PersonView updateSalary(long id, BigDecimal newSalary) {
        log.info("Updating salary of person {} to {}", id, newSalary);
        Person person = requirePerson(id);
        person.setSalary(newSalary);
        return personMapper.toView(personDal.save(person));
    }

    /**
     * Soft delete: flips the deleted flag and (via the DAL save) bumps the replication
     * sequence, so the deletion travels through the replication feed. The row stays in
     * the table but disappears from all regular queries.
     */
    @Transactional
    public boolean deletePerson(long id) {
        Optional<Person> person = personDal.findById(id);
        if (!person.isPresent()) {
            log.info("Delete requested for person {} but it does not exist (or is already deleted)", id);
            return false;
        }
        Person entity = person.get();
        entity.setDeleted(true);
        personDal.save(entity);
        log.info("Soft-deleted person {}", id);
        return true;
    }

    private Person requirePerson(long id) {
        return personDal.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Person", id));
    }

    private void requireUniqueEmail(String email) {
        if (personDal.emailExists(email)) {
            throw new DuplicateResourceException("A person with email " + email + " already exists", "email");
        }
    }

    private List<PersonView> toViews(List<Person> persons) {
        return persons.stream().map(personMapper::toView).collect(Collectors.toList());
    }

    /**
     * Which rows of the batch match the client's filter. Without a filter every row
     * matches; with one, the DAL re-checks the batch's ids against the same
     * dynamically built WHERE clause used everywhere else.
     */
    private Set<Long> matchingIds(List<Person> batch, FilterCriteria criteria) {
        List<Long> ids = batch.stream().map(Person::getId).collect(Collectors.toList());
        if (batch.isEmpty() || criteria.isEmpty()) {
            return new HashSet<>(ids);
        }
        return personDal.matchingIds(ids, criteria);
    }

    private void requirePositiveBulkSize(int bulkSize) {
        if (bulkSize < 1) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT,
                    "Argument 'bulkSize' must be at least 1 but was " + bulkSize,
                    Collections.singletonList(new ErrorDetail("bulkSize", "min", "bulkSize must be >= 1")));
        }
    }
}
