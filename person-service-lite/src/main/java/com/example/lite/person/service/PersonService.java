package com.example.lite.person.service;

import com.example.lite.person.dal.PersonDal;
import com.example.lite.person.domain.Person;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The business layer - stands in for the service classes the migrating application
 * already has. It knows nothing about GraphQL: the fetchers above translate the
 * request into plain method calls, exactly as the old in-house framework did.
 */
@Service
public class PersonService {

    private final PersonDal personDal;

    public PersonService(PersonDal personDal) {
        this.personDal = personDal;
    }

    public List<Person> getAllPersons() {
        return personDal.findAll();
    }

    public Person getPerson(long id) {
        return personDal.findById(id).orElse(null);
    }

    public List<Person> getPersonsByCity(String city) {
        return personDal.findByCity(city);
    }

    public Person createPerson(String firstName, String lastName, String email, String city) {
        return personDal.save(new Person(null, firstName, lastName, email, city));
    }

    public boolean deletePerson(long id) {
        return personDal.deleteById(id);
    }

    public int countPersons() {
        return personDal.count();
    }

    public String fullNameOf(Person person) {
        return person.getFirstName() + " " + person.getLastName();
    }
}
