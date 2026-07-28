package com.example.person.dal;

import com.example.infrastructure.replication.ReplicatedRepository;
import com.example.person.domain.Person;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The replication feed queries are inherited from {@link ReplicatedRepository}; only
 * genuinely person-specific derived queries live here.
 */
@Repository
public interface PersonRepository extends ReplicatedRepository<Person> {

    List<Person> findByAddressCityIgnoreCaseAndDeletedFalse(String city);

    long countByAddressCityIgnoreCaseAndDeletedFalse(String city);

    boolean existsByEmailIgnoreCase(String email);
}
