package com.example.person.dal;

import com.example.person.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    List<Person> findByAddressCityIgnoreCase(String city);

    long countByAddressCityIgnoreCase(String city);

    boolean existsByEmailIgnoreCase(String email);
}
