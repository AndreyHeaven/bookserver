package com.example.bookserver.genres;

import com.example.bookserver.books.BookSearchService;
import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.domain.Genre;
import com.example.bookserver.genres.dto.GenreDetailsDto;
import com.example.bookserver.genres.dto.GenreNodeDto;
import com.example.bookserver.repo.GenreRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class GenresService {

    private final GenreRepository genreRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BookSearchService bookSearchService;

    public GenresService(GenreRepository genreRepository,
                         NamedParameterJdbcTemplate jdbcTemplate,
                         BookSearchService bookSearchService) {
        this.genreRepository = genreRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.bookSearchService = bookSearchService;
    }

    @Cacheable("genresTree")
    public List<GenreNodeDto> tree() {
        List<Genre> genres = sortedGenres();
        Map<Long, Long> counts = hierarchicalBookCounts();
        return buildChildren(null, genres, counts);
    }

    public GenreDetailsDto details(Long id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Genre not found: " + id));
        Map<Long, Long> counts = hierarchicalBookCounts();
        List<Genre> genres = sortedGenres();
        return new GenreDetailsDto(
                genre.getId(),
                genre.getCode(),
                genre.getTitle(),
                genre.getMetaSection(),
                genre.getParent() == null ? null : genre.getParent().getId(),
                genre.getParent() == null ? null : genre.getParent().getTitle(),
                buildChildren(genre.getId(), genres, counts),
                counts.getOrDefault(genre.getId(), 0L));
    }

    public Page<BookCardDto> books(Long id,
                                   boolean includeSubgenres,
                                   String q,
                                   String lang,
                                   Integer yearFrom,
                                   Integer yearTo,
                                   Integer page,
                                   Integer size,
                                   String sort) {
        genreRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Genre not found: " + id));
        return bookSearchService.searchBooks(q, lang, yearFrom, yearTo, List.of(id), null,
                includeSubgenres, page, size, sort, org.springframework.data.domain.Sort.by("title").ascending());
    }

    private List<Genre> sortedGenres() {
        return genreRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Genre g) -> g.getPosition() == null ? Integer.MAX_VALUE : g.getPosition())
                        .thenComparing(Genre::getTitle))
                .toList();
    }

    private List<GenreNodeDto> buildChildren(Long parentId, List<Genre> genres, Map<Long, Long> counts) {
        return genres.stream()
                .filter(g -> parentId == null
                        ? g.getParent() == null
                        : g.getParent() != null && parentId.equals(g.getParent().getId()))
                .map(g -> new GenreNodeDto(
                        g.getId(),
                        g.getCode(),
                        g.getTitle(),
                        g.getMetaSection(),
                        counts.getOrDefault(g.getId(), 0L),
                        buildChildren(g.getId(), genres, counts)))
                .toList();
    }

    private Map<Long, Long> hierarchicalBookCounts() {
        Map<Long, Long> out = new HashMap<>();
        jdbcTemplate.getJdbcTemplate().query("""
                WITH RECURSIVE genre_descendants(root_id, descendant_id) AS (
                    SELECT id, id FROM genres
                    UNION ALL
                    SELECT gd.root_id, g.id
                      FROM genre_descendants gd
                      JOIN genres g ON g.parent_id = gd.descendant_id
                )
                SELECT gd.root_id, COUNT(DISTINCT bg.book_id) AS book_count
                  FROM genre_descendants gd
                  LEFT JOIN book_genres bg ON bg.genre_id = gd.descendant_id
                  LEFT JOIN books b ON b.id = bg.book_id AND b.deleted = false
                 GROUP BY gd.root_id
                """, (RowCallbackHandler) rs -> out.put(rs.getLong("root_id"), rs.getLong("book_count")));
        return out;
    }
}
