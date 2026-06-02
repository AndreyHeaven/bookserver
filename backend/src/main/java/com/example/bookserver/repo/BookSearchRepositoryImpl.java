package com.example.bookserver.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-SQL implementation of {@link BookSearchRepository}.
 *
 * <p>Search:
 * <pre>
 *   SELECT id, title, year, lang, file_type,
 *          ts_rank_cd(b.fts_tsv, plainto_tsquery('russian', :q)) AS rank
 *     FROM books b [JOIN book_genres bg ON bg.book_id = b.id]
 *    WHERE b.fts_tsv @@ plainto_tsquery('russian', :q)
 *      [AND b.lang = :lang]
 *      [AND b.year = :year]
 *      [AND bg.genre_id IN (:genreIds)]
 *    ORDER BY rank DESC
 *    LIMIT :limit OFFSET :offset
 * </pre>
 *
 * <p>Facet counts: three small GROUP BY queries (lang, year, genre) over the
 * same predicate. Each facet skips its own dimension's filter so that the
 * histogram does not collapse to the currently-selected value only.
 *
 * <p>Rows are mapped manually from {@code Object[]} into
 * {@link BookSearchProjection} / typed maps to avoid coupling to a Hibernate
 * {@code ResultTransformer}.
 */
public class BookSearchRepositoryImpl implements BookSearchRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<BookSearchProjection> search(String query, FacetFilter filter, Pageable pageable) {
        boolean hasQuery = query != null && !query.isBlank();
        boolean needsGenreJoin = filter != null && filter.genreIds() != null && !filter.genreIds().isEmpty();

        Map<String, Object> params = new HashMap<>();
        StringBuilder from = new StringBuilder("FROM books b ");
        if (needsGenreJoin) {
            from.append("JOIN book_genres bg ON bg.book_id = b.id ");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (hasQuery) {
            where.append(" AND b.fts_tsv @@ plainto_tsquery('russian', :q) ");
            params.put("q", query);
        }
        if (filter != null) {
            if (filter.lang() != null && !filter.lang().isBlank()) {
                where.append(" AND b.lang = :lang ");
                params.put("lang", filter.lang());
            }
            if (filter.year() != null) {
                where.append(" AND b.year = :year ");
                params.put("year", filter.year());
            }
            if (needsGenreJoin) {
                where.append(" AND bg.genre_id IN (:genreIds) ");
                params.put("genreIds", filter.genreIds());
            }
        }

        String rankExpr = hasQuery
                ? "ts_rank_cd(b.fts_tsv, plainto_tsquery('russian', :q))"
                : "0.0";
        String orderBy = hasQuery ? " ORDER BY rank DESC, b.id ASC " : " ORDER BY b.title ASC, b.id ASC ";

        String sql = "SELECT b.id, b.title, b.year, b.lang, b.file_type, " + rankExpr + " AS rank "
                + from + where + orderBy + " LIMIT :limit OFFSET :offset";
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        q.setParameter("limit", pageable.getPageSize());
        q.setParameter("offset", pageable.getOffset());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<BookSearchProjection> content = rows.stream()
                .map(r -> new BookSearchProjection(
                        toLong(r[0]),
                        (String) r[1],
                        toInteger(r[2]),
                        (String) r[3],
                        (String) r[4],
                        toDouble(r[5])))
                .toList();

        String countSql = "SELECT COUNT(*) FROM ( SELECT b.id " + from + where + " ) sub";
        Query cq = em.createNativeQuery(countSql);
        params.forEach(cq::setParameter);
        long total = ((Number) cq.getSingleResult()).longValue();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public FacetCounts facetCounts(String query, FacetFilter filter) {
        FacetFilter f = filter == null ? FacetFilter.empty() : filter;
        Map<String, Long> langs = aggregate(
                "b.lang", query, f, /*skipLang*/ true, /*skipYear*/ false, /*skipGenre*/ false);
        Map<Integer, Long> years = aggregate(
                "b.year", query, f, false, true, false)
                .entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(Integer.parseInt(e.getKey()), e.getValue()),
                        Map::putAll);
        Map<Long, Long> genres = aggregateGenres(query, f);
        return new FacetCounts(langs, years, genres);
    }

    private Map<String, Long> aggregate(String groupBy, String query, FacetFilter filter,
                                        boolean skipLang, boolean skipYear, boolean skipGenre) {
        boolean hasQuery = query != null && !query.isBlank();
        boolean needsGenreJoin = !skipGenre
                && filter.genreIds() != null && !filter.genreIds().isEmpty();

        Map<String, Object> params = new HashMap<>();
        StringBuilder from = new StringBuilder("FROM books b ");
        if (needsGenreJoin) {
            from.append("JOIN book_genres bg ON bg.book_id = b.id ");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (hasQuery) {
            where.append(" AND b.fts_tsv @@ plainto_tsquery('russian', :q) ");
            params.put("q", query);
        }
        if (!skipLang && filter.lang() != null && !filter.lang().isBlank()) {
            where.append(" AND b.lang = :lang ");
            params.put("lang", filter.lang());
        }
        if (!skipYear && filter.year() != null) {
            where.append(" AND b.year = :year ");
            params.put("year", filter.year());
        }
        if (needsGenreJoin) {
            where.append(" AND bg.genre_id IN (:genreIds) ");
            params.put("genreIds", filter.genreIds());
        }

        String sql = "SELECT " + groupBy + " AS g, COUNT(DISTINCT b.id) AS c "
                + from + where + " GROUP BY g ORDER BY c DESC";
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? null : row[0].toString();
            out.put(key, ((Number) row[1]).longValue());
        }
        return out;
    }

    private Map<Long, Long> aggregateGenres(String query, FacetFilter filter) {
        boolean hasQuery = query != null && !query.isBlank();
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (hasQuery) {
            where.append(" AND b.fts_tsv @@ plainto_tsquery('russian', :q) ");
            params.put("q", query);
        }
        if (filter.lang() != null && !filter.lang().isBlank()) {
            where.append(" AND b.lang = :lang ");
            params.put("lang", filter.lang());
        }
        if (filter.year() != null) {
            where.append(" AND b.year = :year ");
            params.put("year", filter.year());
        }
        String sql = "SELECT bg.genre_id, COUNT(DISTINCT b.id) "
                + " FROM books b JOIN book_genres bg ON bg.book_id = b.id "
                + where + " GROUP BY bg.genre_id ORDER BY 2 DESC";
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            out.put(toLong(row[0]), ((Number) row[1]).longValue());
        }
        return out;
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer toInteger(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static Double toDouble(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }
}
