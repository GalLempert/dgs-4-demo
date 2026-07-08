package com.example.person.dal;

import com.example.person.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByAddressCityIgnoreCase(String city);

    boolean existsByEmailIgnoreCase(String email);
}
