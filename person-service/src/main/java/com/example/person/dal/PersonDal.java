package com.example.person.dal;

import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.filter.FilterSpecificationBuilder;
import com.example.infrastructure.filter.QueryResultCap;
import com.example.person.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for the person domain. The only place that touches the repository;
 * the service layer depends on this class, never on Spring Data directly.
 *
 * <p>Filtered reads build their WHERE clause dynamically from the
 * {@link FilterCriteria} and are guarded by the {@link QueryResultCap}: the matching
 * rows are counted first and the query is rejected before fetching when the count
 * exceeds the cap.
 */
@Component
public class PersonDal {

    private static final String RESOURCE_NAME = "Person";

    private static final Logger log = LoggerFactory.getLogger(PersonDal.class);

    private final PersonRepository personRepository;
    private final FilterSpecificationBuilder specificationBuilder;
    private final QueryResultCap queryResultCap;

    public PersonDal(PersonRepository personRepository,
                     FilterSpecificationBuilder specificationBuilder,
                     QueryResultCap queryResultCap) {
        this.personRepository = personRepository;
        this.specificationBuilder = specificationBuilder;
        this.queryResultCap = queryResultCap;
    }

    public Optional<Person> findById(long id) {
        log.debug("DB: findById({})", id);
        return personRepository.findById(id);
    }

    /** Filtered (or unfiltered, with {@link FilterCriteria#none()}) capped list query. */
    public List<Person> findAll(FilterCriteria criteria) {
        log.debug("DB: findAll({})", criteria);
        Specification<Person> specification = specificationBuilder.toSpecification(criteria);
        queryResultCap.enforce(RESOURCE_NAME, personRepository.count(specification));
        return personRepository.findAll(specification);
    }

    public List<Person> findByCity(String city) {
        log.debug("DB: findByCity('{}')", city);
        queryResultCap.enforce(RESOURCE_NAME, personRepository.countByAddressCityIgnoreCase(city));
        return personRepository.findByAddressCityIgnoreCase(city);
    }

    public boolean emailExists(String email) {
        log.debug("DB: emailExists('{}')", email);
        return personRepository.existsByEmailIgnoreCase(email);
    }

    public boolean exists(long id) {
        log.debug("DB: exists({})", id);
        return personRepository.existsById(id);
    }

    public Person save(Person person) {
        log.debug("DB: save(person id={})", person.getId());
        return personRepository.save(person);
    }

    public void deleteById(long id) {
        log.debug("DB: deleteById({})", id);
        personRepository.deleteById(id);
    }
}
