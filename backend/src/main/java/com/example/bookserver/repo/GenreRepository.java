package com.example.bookserver.repo;

import com.example.bookserver.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByCode(String code);
}
