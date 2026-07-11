package com.example.person.service;

import com.example.infrastructure.error.DuplicateResourceException;
import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.person.dal.PersonDal;
import com.example.person.domain.Person;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

    @Transactional
    public boolean deletePerson(long id) {
        if (!personDal.exists(id)) {
            log.info("Delete requested for person {} but it does not exist", id);
            return false;
        }
        personDal.deleteById(id);
        log.info("Deleted person {}", id);
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
}
