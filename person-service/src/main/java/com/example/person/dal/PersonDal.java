package com.example.person.dal;

import com.example.infrastructure.replication.ResourceDal;
import com.example.infrastructure.replication.ResourceDalSupport;
import com.example.person.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Data access layer for the person domain. All standard behavior - capped filtered
 * reads, the replication feed reads, sequence stamping on save, soft-delete
 * visibility - is inherited from {@link ResourceDal}; only person-specific queries
 * are implemented here. Still the only class that touches the repository.
 */
@Component
public class PersonDal extends ResourceDal<Person> {

    private static final Logger log = LoggerFactory.getLogger(PersonDal.class);

    private final PersonRepository personRepository;

    public PersonDal(PersonRepository personRepository, ResourceDalSupport support) {
        super("Person", "person_replication_seq", personRepository, support);
        this.personRepository = personRepository;
    }

    public List<Person> findByCity(String city) {
        log.debug("DB: findByCity('{}')", city);
        resultCap().enforce(resourceName(), personRepository.countByAddressCityIgnoreCaseAndDeletedFalse(city));
        return personRepository.findByAddressCityIgnoreCaseAndDeletedFalse(city);
    }

    public boolean emailExists(String email) {
        log.debug("DB: emailExists('{}')", email);
        return personRepository.existsByEmailIgnoreCase(email);
    }
}
