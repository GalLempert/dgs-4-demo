package com.example.person.service;

import com.example.infrastructure.error.DuplicateResourceException;
import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.mapping.DeclarativeMapper;
import com.example.infrastructure.replication.ResourceService;
import com.example.person.dal.PersonDal;
import com.example.person.domain.Person;
import com.example.person.domain.PhoneNumber;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Business layer of the person domain. The standard behavior of a replicated resource
 * (filtered find, replication feed, count, max sequence, the five standard mutations,
 * soft delete) is inherited from {@link ResourceService}; this class adds the
 * person-specific rules (email uniqueness, the email natural key, salary updates,
 * city lookup, phone-number back-references) and the view mapping enriched by
 * {@link PersonCalculations} through the {@link PersonMapper}.
 */
@Service
public class PersonService extends ResourceService<Person, PersonView> {

    private static final Logger log = LoggerFactory.getLogger(PersonService.class);

    private final PersonDal personDal;
    private final PersonMapper personMapper;

    public PersonService(PersonDal personDal, PersonMapper personMapper, DeclarativeMapper declarativeMapper) {
        super(personDal, declarativeMapper, Person.class);
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
        return saveNew(input);
    }

    @Transactional
    public PersonView updateSalary(long id, BigDecimal newSalary) {
        log.info("Updating salary of person {} to {}", id, newSalary);
        Person person = requirePerson(id);
        person.setSalary(newSalary);
        return personMapper.toView(personDal.save(person));
    }

    // --------------------------------------------- inherited mutation pipeline hooks

    /** Business rule enforced on every mutation: a new person's email must be unique. */
    @Override
    protected void validate(Person person) {
        if (person.getId() == null) {
            requireUniqueEmail(person.getEmail());
        }
    }

    /** What identifies "the same person" for saveOrOverride: the unique email. */
    @Override
    protected FilterCriteria naturalKeyOf(Object input) {
        return FilterCriteria.whereEquals("email", ((CreatePersonInput) input).getEmail());
    }

    @Override
    protected Person mergeIntoEntity(Object input, Person person) {
        return relinkPhoneNumbers(super.mergeIntoEntity(input, person));
    }

    @Override
    protected Person overrideEntity(Object input, Person person) {
        return relinkPhoneNumbers(super.overrideEntity(input, person));
    }

    /**
     * The declarative merge refills the existing phone-number collection from the
     * input, but children deserialized into an already-existing collection have no
     * parent yet ({@code @JsonManagedReference} only wires them when parent and
     * children are built together, as on the create path). Re-adding through the
     * aggregate's own method restores the back-references.
     */
    private Person relinkPhoneNumbers(Person person) {
        List<PhoneNumber> phoneNumbers = new ArrayList<>(person.getPhoneNumbers());
        person.getPhoneNumbers().clear();
        phoneNumbers.forEach(person::addPhoneNumber);
        return person;
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
