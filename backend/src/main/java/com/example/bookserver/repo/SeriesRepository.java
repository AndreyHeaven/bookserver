package com.example.bookserver.repo;

import com.example.bookserver.domain.Series;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    Optional<Series> findByTitle(String title);
}
