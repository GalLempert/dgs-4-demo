package com.example.lite.person.dal;

import com.example.lite.person.domain.Person;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The data access layer - deliberately a trivial in-memory store. It stands in for
 * whatever DAL the migrating service already has (JPA, JDBC, a remote client...):
 * the GraphQL layer above never knows the difference, which is the point.
 */
@Component
public class PersonDal {

    private final Map<Long, Person> personsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public List<Person> findAll() {
        return personsById.values().stream()
                .sorted(Comparator.comparing(Person::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Optional<Person> findById(long id) {
        return Optional.ofNullable(personsById.get(id));
    }

    public List<Person> findByCity(String city) {
        return findAll().stream()
                .filter(person -> person.getCity() != null && person.getCity().equalsIgnoreCase(city))
                .collect(Collectors.toList());
    }

    public Person save(Person person) {
        if (person.getId() == null) {
            person.setId(idSequence.incrementAndGet());
        }
        personsById.put(person.getId(), person);
        return person;
    }

    public boolean deleteById(long id) {
        return personsById.remove(id) != null;
    }

    public int count() {
        return personsById.size();
    }
}
