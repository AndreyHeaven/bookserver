package com.example.bookserver.authors;

import com.example.bookserver.authors.dto.AlphabetBucketDto;
import com.example.bookserver.authors.dto.AuthorCardDto;
import com.example.bookserver.authors.dto.AuthorDetailsDto;
import com.example.bookserver.authors.dto.AuthorSearchRequest;
import com.example.bookserver.books.BookSearchService;
import com.example.bookserver.books.dto.BookCardDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AuthorsService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BookSearchService bookSearchService;

    public AuthorsService(NamedParameterJdbcTemplate jdbcTemplate, BookSearchService bookSearchService) {
        this.jdbcTemplate = jdbcTemplate;
        this.bookSearchService = bookSearchService;
    }

    public Page<AuthorCardDto> search(AuthorSearchRequest request) {
        Pageable pageable = PageRequest.of(
                request.page() == null ? 0 : request.page(),
                Math.min(request.size() == null ? 20 : request.size(), 100),
                parseSort(request.sort()));
        QueryParts parts = authorWhere(request.q(), request.letter());
        String orderBy = authorOrderBy(pageable.getSort(), request.q());
        String sql = """
                SELECT p.id, p.last_name, p.first_name, p.middle_name, COUNT(DISTINCT ba.book_id) AS book_count
                  FROM persons p
                  LEFT JOIN book_authors ba ON ba.person_id = p.id
                  LEFT JOIN books b ON b.id = ba.book_id AND b.deleted = false
                """ + parts.where()
                + " GROUP BY p.id, p.last_name, p.first_name, p.middle_name "
                + orderBy
                + " LIMIT :limit OFFSET :offset";
        Map<String, Object> params = new HashMap<>(parts.params());
        params.put("limit", pageable.getPageSize());
        params.put("offset", pageable.getOffset());
        List<AuthorCardDto> content = jdbcTemplate.query(sql, params, (rs, rowNum) -> new AuthorCardDto(
                rs.getLong("id"),
                rs.getString("last_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                fullName(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                rs.getLong("book_count")));
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM persons p " + parts.where(),
                parts.params(), Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public AuthorDetailsDto details(Long id) {
        List<AuthorDetailsDto> rows = jdbcTemplate.query("""
                SELECT p.id, p.last_name, p.first_name, p.middle_name, COUNT(DISTINCT ba.book_id) AS book_count
                  FROM persons p
                  LEFT JOIN book_authors ba ON ba.person_id = p.id
                  LEFT JOIN books b ON b.id = ba.book_id AND b.deleted = false
                 WHERE p.id = :id
                 GROUP BY p.id, p.last_name, p.first_name, p.middle_name
                """, Map.of("id", id), (rs, rowNum) -> new AuthorDetailsDto(
                rs.getLong("id"),
                rs.getString("last_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                fullName(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                rs.getLong("book_count")));
        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Author not found: " + id);
        }
        return rows.get(0);
    }

    public Page<BookCardDto> books(Long authorId,
                                   String q,
                                   String lang,
                                   Integer yearFrom,
                                   Integer yearTo,
                                   List<Long> genreIds,
                                   Integer page,
                                   Integer size,
                                   String sort) {
        details(authorId);
        return bookSearchService.searchBooks(q, lang, yearFrom, yearTo, genreIds, authorId,
                true, page, size, sort, BookSearchService.authorBooksSort());
    }

    public List<AlphabetBucketDto> alphabet() {
        return jdbcTemplate.getJdbcTemplate().query("""
                SELECT upper(substring(p.last_name from 1 for 1)) AS letter, COUNT(*) AS count
                  FROM persons p
                 WHERE p.last_name IS NOT NULL AND p.last_name <> ''
                 GROUP BY letter
                 ORDER BY letter
                """, (rs, rowNum) -> new AlphabetBucketDto(rs.getString("letter"), rs.getLong("count")));
    }

    private QueryParts authorWhere(String q, String letter) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (q != null && !q.isBlank()) {
            where.append(" AND p.fts_tsv @@ plainto_tsquery('russian', :q) ");
            params.put("q", q);
        }
        if (letter != null && !letter.isBlank()) {
            where.append(" AND upper(substring(p.last_name from 1 for 1)) = upper(:letter) ");
            params.put("letter", letter);
        }
        return new QueryParts(where.toString(), params);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("lastName").ascending().and(Sort.by("firstName").ascending());
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!List.of("lastName", "firstName", "middleName", "bookCount", "id").contains(property)) {
            return Sort.by("lastName").ascending().and(Sort.by("firstName").ascending());
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private String authorOrderBy(Sort sort, String q) {
        if (sort.isUnsorted() && q != null && !q.isBlank()) {
            return " ORDER BY ts_rank_cd(p.fts_tsv, plainto_tsquery('russian', :q)) DESC, p.id ASC ";
        }
        List<String> clauses = sort.stream().map(order -> switch (order.getProperty()) {
            case "lastName" -> "p.last_name " + direction(order) + " NULLS LAST";
            case "firstName" -> "p.first_name " + direction(order) + " NULLS LAST";
            case "middleName" -> "p.middle_name " + direction(order) + " NULLS LAST";
            case "bookCount" -> "book_count " + direction(order);
            case "id" -> "p.id " + direction(order);
            default -> null;
        }).filter(java.util.Objects::nonNull).toList();
        if (clauses.isEmpty()) {
            clauses = List.of("p.last_name ASC NULLS LAST", "p.first_name ASC NULLS LAST");
        }
        return " ORDER BY " + String.join(", ", clauses) + ", p.id ASC ";
    }

    private static String direction(Sort.Order order) {
        return order.isDescending() ? "DESC" : "ASC";
    }

    private static String fullName(String lastName, String firstName, String middleName) {
        return String.join(" ", List.of(nullToBlank(lastName), nullToBlank(firstName), nullToBlank(middleName)))
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record QueryParts(String where, Map<String, Object> params) {
    }
}
