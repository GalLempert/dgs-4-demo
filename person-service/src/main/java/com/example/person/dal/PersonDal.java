package com.example.person.dal;

import com.example.person.domain.Person;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for the person domain. The only place that touches the repository;
 * the service layer depends on this class, never on Spring Data directly.
 */
@Component
public class PersonDal {

    private final PersonRepository personRepository;

    public PersonDal(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Optional<Person> findById(long id) {
        return personRepository.findById(id);
    }

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public List<Person> findByCity(String city) {
        return personRepository.findByAddressCityIgnoreCase(city);
    }

    public boolean emailExists(String email) {
        return personRepository.existsByEmailIgnoreCase(email);
    }

    public boolean exists(long id) {
        return personRepository.existsById(id);
    }

    public Person save(Person person) {
        return personRepository.save(person);
    }

    public void deleteById(long id) {
        personRepository.deleteById(id);
    }
}
