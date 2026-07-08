package com.example.person.dal;

import com.example.person.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for the person domain. The only place that touches the repository;
 * the service layer depends on this class, never on Spring Data directly.
 */
@Component
public class PersonDal {

    private static final Logger log = LoggerFactory.getLogger(PersonDal.class);

    private final PersonRepository personRepository;

    public PersonDal(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Optional<Person> findById(long id) {
        log.debug("DB: findById({})", id);
        return personRepository.findById(id);
    }

    public List<Person> findAll() {
        log.debug("DB: findAll()");
        return personRepository.findAll();
    }

    public List<Person> findByCity(String city) {
        log.debug("DB: findByCity('{}')", city);
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
