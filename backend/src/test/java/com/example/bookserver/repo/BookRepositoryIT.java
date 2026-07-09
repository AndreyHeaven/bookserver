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
import java.util.UUID;

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
                "Война", new FacetFilter(List.of("en"), null, List.of()), PageRequest.of(0, 10));
        assertThat(wrongLang.getTotalElements()).isZero();
    }

    /**
     * Covers A-F11 (rank is NULL when query is blank) and validates
     * the (title ASC, id ASC) browse-order fallback.
     */
    @Test
    @Transactional
    void search_with_blank_query_orders_by_title() {
        createBook("Бета", "ru", 2000);
        createBook("Альфа", "ru", 2001);
        createBook("Гамма", "ru", 2002);
        em.flush();
        em.clear();

        Page<BookSearchProjection> page = bookRepository.search(
                null, FacetFilter.empty(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(BookSearchProjection::title)
                .containsExactly("Альфа", "Бета", "Гамма");
        assertThat(page.getContent()).allSatisfy(
                hit -> assertThat(hit.rank()).as("rank is null for blank query").isNull());

        // Empty string is treated the same as null
        Page<BookSearchProjection> pageEmpty = bookRepository.search(
                "", FacetFilter.empty(), PageRequest.of(0, 10));
        assertThat(pageEmpty.getTotalElements()).isEqualTo(3);
        assertThat(pageEmpty.getContent().get(0).rank()).isNull();
    }

    /**
     * Covers A-F1 (multi-genre filter must not duplicate rows when a book
     * belongs to multiple genres listed in the filter).
     */
    @Test
    @Transactional
    void search_with_multi_genre_filter_does_not_duplicate() {
        Genre g1 = createGenre("g1");
        Genre g2 = createGenre("g2");

        Book b1 = newBook("B1-multi", "ru", 2000);
        b1.getGenres().add(g1);
        b1.getGenres().add(g2);
        bookRepository.save(b1);

        Book b2 = newBook("B2-single", "ru", 2001);
        b2.getGenres().add(g1);
        bookRepository.save(b2);

        em.flush();
        em.clear();

        Page<BookSearchProjection> page = bookRepository.search(
                null,
                new FacetFilter(null, null, List.of(g1.getId(), g2.getId())),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements())
                .as("b1 must not be duplicated despite matching two genres")
                .isEqualTo(2);
        long distinctIds = page.getContent().stream()
                .map(BookSearchProjection::id).distinct().count();
        assertThat(distinctIds).isEqualTo(page.getContent().size());
    }

    /**
     * Covers A-F4 / B-F3 — soft-deleted books must not appear in search results
     * nor contribute to facet histograms.
     */
    @Test
    @Transactional
    void deleted_books_are_excluded_from_search_and_facets() {
        Genre g = createGenre("g-del");
        Book alive = newBook("Alive", "ru", 2000);
        alive.getGenres().add(g);
        bookRepository.save(alive);

        Book deleted = newBook("Deleted", "en", 1999);
        deleted.getGenres().add(g);
        deleted.setDeleted(true);
        bookRepository.save(deleted);

        em.flush();
        em.clear();

        Page<BookSearchProjection> page = bookRepository.search(
                null, FacetFilter.empty(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(BookSearchProjection::title)
                .containsExactly("Alive");

        FacetCounts facets = bookRepository.facetCounts(null, FacetFilter.empty());
        assertThat(facets.langs()).containsOnlyKeys("ru");
        assertThat(facets.langs()).containsEntry("ru", 1L);
        assertThat(facets.years()).containsOnlyKeys(2000);
        assertThat(facets.genres()).containsEntry(g.getId(), 1L);
    }

    /**
     * Covers B-F2 — when a facet dimension is filtered, that facet's own
     * histogram still returns the full set of options (skip-self filter).
     */
    @Test
    @Transactional
    void facet_counts_apply_skip_self_filter() {
        createBook("RuBook", "ru", 2000);
        createBook("EnBook", "en", 2001);
        em.flush();
        em.clear();

        FacetCounts facets = bookRepository.facetCounts(
                null, new FacetFilter(List.of("ru"), null, null));

        // lang facet ignores the lang filter (skip-self) → both options listed
        assertThat(facets.langs()).containsEntry("ru", 1L);
        assertThat(facets.langs()).containsEntry("en", 1L);

        // years facet, however, DOES apply the lang filter → only ru's year present
        assertThat(facets.years()).containsOnlyKeys(2000);
        assertThat(facets.years()).containsEntry(2000, 1L);
    }

    /**
     * Covers B-F2 / B-F10 — verify the two FTS triggers cooperate:
     * 1) book without authors gets indexed by title only (trg_books_fts);
     * 2) attaching a BookAuthor later triggers trg_book_authors_fts which
     *    re-computes fts_tsv to include the author's last name.
     */
    @Test
    @Transactional
    void book_without_authors_is_indexed_by_title_only_and_recomputed_when_author_added() {
        Book book = newBook("Сольное произведение", "ru", 1995);
        book = bookRepository.save(book);
        em.flush();
        em.clear();

        String ftsBefore = (String) em.createNativeQuery(
                        "SELECT fts_tsv::text FROM books WHERE id = ?1")
                .setParameter(1, book.getId())
                .getSingleResult();
        assertThat(ftsBefore).as("fts_tsv populated even without authors").isNotBlank();
        // PG russian stemmer reduces "произведение" to "произведен"
        assertThat(ftsBefore.toLowerCase()).contains("произведен");

        // Now attach an author and verify the AFTER INSERT trigger recomputes fts_tsv.
        Person dostoevsky = new Person();
        dostoevsky.setLastName("Достоевский");
        dostoevsky.setFirstName("Фёдор");
        dostoevsky = personRepository.save(dostoevsky);

        Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
        reloaded.getAuthors().add(new BookAuthor(reloaded, dostoevsky, 0));
        bookRepository.save(reloaded);
        em.flush();
        em.clear();

        String ftsAfter = (String) em.createNativeQuery(
                        "SELECT fts_tsv::text FROM books WHERE id = ?1")
                .setParameter(1, book.getId())
                .getSingleResult();
        assertThat(ftsAfter.toLowerCase())
                .as("trg_book_authors_fts must add the author's last name to fts_tsv")
                .contains("достоевск");
    }

    // ---------- helpers ----------

    private Book newBook(String title, String lang, int year) {
        Book b = new Book();
        b.setTitle(title);
        b.setLang(lang);
        b.setYear(year);
        b.setFileType("fb2");
        return b;
    }

    private Book createBook(String title, String lang, int year) {
        Book b = newBook(title, lang, year);
        b = bookRepository.save(b);
        return b;
    }

    private Genre createGenre(String codePrefix) {
        Genre g = new Genre();
        g.setCode(codePrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        g.setTitle("Genre " + codePrefix);
        g.setMetaSection("test");
        g.setPosition(0);
        return genreRepository.save(g);
    }
}
