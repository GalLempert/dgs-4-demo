package com.example.person.dal;

import com.example.person.domain.Person;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    List<Person> findByAddressCityIgnoreCaseAndDeletedFalse(String city);

    long countByAddressCityIgnoreCaseAndDeletedFalse(String city);

    boolean existsByEmailIgnoreCase(String email);

    /** The replication feed page: next rows strictly after the given sequence. */
    List<Person> findBySequenceGreaterThanOrderBySequenceAsc(long sequence, Pageable pageable);

    /** Highest replication sequence in the table, 0 when the table is empty. */
    @Query("select coalesce(max(p.sequence), 0) from Person p")
    long maxSequence();
}
