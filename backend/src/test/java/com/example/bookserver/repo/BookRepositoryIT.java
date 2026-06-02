package com.example.bookserver.repo;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookAuthor;
import com.example.bookserver.domain.Genre;
import com.example.bookserver.domain.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookRepositoryIT extends AbstractIntegrationTest {

    @Autowired BookRepository bookRepository;
    @Autowired PersonRepository personRepository;
    @Autowired GenreRepository genreRepository;
    @PersistenceContext EntityManager em;

    @Test
    @Transactional
    void save_and_load_book_with_author_and_genre_and_search_by_fts() {
        // Person
        Person tolstoy = new Person();
        tolstoy.setLastName("Толстой");
        tolstoy.setFirstName("Лев");
        tolstoy.setMiddleName("Николаевич");
        tolstoy = personRepository.save(tolstoy);

        // Genre
        Genre genre = new Genre();
        genre.setCode("classic_rus");
        genre.setTitle("Русская классика");
        genre.setMetaSection("Литература");
        genre.setPosition(1);
        genre = genreRepository.save(genre);

        // Book
        Book book = new Book();
        book.setTitle("Война и мир");
        book.setLang("ru");
        book.setYear(1869);
        book.setFileType("fb2");
        book.setKeywords("исторический роман эпопея");
        book.setMd5("aabbccddeeff00112233445566778899");
        book.getGenres().add(genre);
        book.getAuthors().add(new BookAuthor(book, tolstoy, 0));
        book = bookRepository.save(book);

        // Trigger FTS recompute (BEFORE INSERT trg_books_fts fires on book insert,
        // AFTER INSERT trg_book_authors_fts fires on book_authors insert).
        em.flush();
        em.clear();

        // Read back
        Book loaded = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Война и мир");
        assertThat(loaded.getAuthors()).hasSize(1);
        assertThat(loaded.getAuthors().iterator().next().getPerson().getLastName()).isEqualTo("Толстой");
        assertThat(loaded.getGenres()).extracting(Genre::getCode).containsExactly("classic_rus");

        // fts_tsv must have been populated by triggers
        Object ftsTsv = em.createNativeQuery("SELECT fts_tsv FROM books WHERE id = ?1")
                .setParameter(1, book.getId())
                .getSingleResult();
        assertThat(ftsTsv).as("fts_tsv populated by trigger").isNotNull();
        assertThat(ftsTsv.toString()).isNotBlank();

        // Search by a fragment of the title (russian morphology -> "война" lemma)
        Page<BookSearchProjection> page = bookRepository.search(
                "Война", FacetFilter.empty(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        BookSearchProjection hit = page.getContent().get(0);
        assertThat(hit.id()).isEqualTo(book.getId());
        assertThat(hit.title()).isEqualTo("Война и мир");
        assertThat(hit.lang()).isEqualTo("ru");
        assertThat(hit.year()).isEqualTo(1869);
        assertThat(hit.rank()).isGreaterThan(0.0);

        // Search by author name (weight B) — must match through trg_book_authors_fts
        Page<BookSearchProjection> byAuthor = bookRepository.search(
                "Толстой", FacetFilter.empty(), PageRequest.of(0, 10));
        assertThat(byAuthor.getTotalElements())
                .as("author-driven FTS match via trg_book_authors_fts")
                .isEqualTo(1);

        // Facet counts must be non-empty
        FacetCounts facets = bookRepository.facetCounts("Война", FacetFilter.empty());
        assertThat(facets.langs()).containsEntry("ru", 1L);
        assertThat(facets.years()).containsEntry(1869, 1L);
        assertThat(facets.genres()).containsEntry(genre.getId(), 1L);

        // Filter by genre via facet -> still finds the book
        Page<BookSearchProjection> byGenre = bookRepository.search(
                "Война",
                new FacetFilter(null, null, List.of(genre.getId())),
                PageRequest.of(0, 10));
        assertThat(byGenre.getTotalElements()).isEqualTo(1);

        // Negative filter — wrong lang -> empty page
        Page<BookSearchProjection> wrongLang = bookRepository.search(
                "Война", new FacetFilter("en", null, List.of()), PageRequest.of(0, 10));
        assertThat(wrongLang.getTotalElements()).isZero();
    }
}
