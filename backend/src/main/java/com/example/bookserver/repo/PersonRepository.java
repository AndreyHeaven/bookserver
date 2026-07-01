package com.example.bookserver.repo;

import com.example.bookserver.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByLastNameAndFirstNameAndMiddleName(String lastName, String firstName, String middleName);
}
