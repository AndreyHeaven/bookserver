package com.example.bookserver.repo;

import com.example.bookserver.domain.BookListShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookListShareRepository extends JpaRepository<BookListShare, Long> {

    Optional<BookListShare> findByShareToken(String shareToken);
}
