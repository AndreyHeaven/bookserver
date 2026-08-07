package com.example.bookserver.repo;

import com.example.bookserver.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long>, BookSearchRepository {

    Optional<Book> findByMd5(String md5);

    @Override
    @EntityGraph(attributePaths = {"authors", "authors.person", "files"})
    List<Book> findAllById(Iterable<Long> ids);

    @Query("SELECT b.md5 FROM Book b WHERE b.md5 IN :md5s")
    Set<String> findExistingMd5s(@Param("md5s") Collection<String> md5s);

    boolean existsByArchiveName(String archiveName);

    /** Most recently imported books first — backs the OPDS "New" acquisition feed. */
    Page<Book> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /** Non-deleted books authored by the given person, ordered by title. */
    @Query("""
            SELECT b FROM Book b
              JOIN b.authors ba
             WHERE ba.person.id = :authorId AND b.deleted = false
             ORDER BY b.title ASC
            """)
    Page<Book> findByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    /** Non-deleted books directly tagged with the given genre, ordered by title. */
    @Query("""
            SELECT b FROM Book b
              JOIN b.genres g
             WHERE g.id = :genreId AND b.deleted = false
             ORDER BY b.title ASC
            """)
    Page<Book> findByGenreId(@Param("genreId") Long genreId, Pageable pageable);
}
