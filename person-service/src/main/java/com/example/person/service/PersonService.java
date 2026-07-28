package com.example.person.service;

import com.example.infrastructure.error.DuplicateResourceException;
import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.replication.ReplicatedResourceService;
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

/**
 * Business layer of the person domain. The standard behavior of a replicated resource
 * (filtered find, replication feed, count, max sequence, soft delete) is inherited
 * from {@link ReplicatedResourceService}; this class adds the person-specific rules
 * (email uniqueness, salary updates, city lookup) and the view mapping enriched by
 * {@link PersonCalculations} through the {@link PersonMapper}.
 */
@Service
public class PersonService extends ReplicatedResourceService<Person, PersonView> {

    private static final Logger log = LoggerFactory.getLogger(PersonService.class);

    private final PersonDal personDal;
    private final PersonMapper personMapper;

    public PersonService(PersonDal personDal, PersonMapper personMapper) {
        super(personDal);
        this.personDal = personDal;
        this.personMapper = personMapper;
    }

    @Override
    protected PersonView toView(Person person) {
        return personMapper.toView(person);
    }

    @Transactional(readOnly = true)
    public PersonView getPerson(long id) {
        log.debug("Fetching person {}", id);
        return personMapper.toView(requirePerson(id));
    }

    @Transactional(readOnly = true)
    public List<PersonView> getAllPersons() {
        return find(FilterCriteria.none());
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

    private Person requirePerson(long id) {
        return personDal.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Person", id));
    }

    private void requireUniqueEmail(String email) {
        if (personDal.emailExists(email)) {
            throw new DuplicateResourceException("A person with email " + email + " already exists", "email");
        }
    }
}
