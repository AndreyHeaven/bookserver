package com.example.bookserver.repo;

import com.example.bookserver.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, BookSearchRepository {

    Optional<Book> findByMd5(String md5);
}
