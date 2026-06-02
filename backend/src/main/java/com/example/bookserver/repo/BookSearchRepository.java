package com.example.bookserver.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Custom repository fragment for PG full-text search on {@code books}.
 * Wired in by Spring Data through the matching {@code Impl} class name.
 */
public interface BookSearchRepository {

    /**
     * Run a FTS query, optionally narrowed by facet filters, returning a
     * page of lightweight projections sorted by descending {@code ts_rank_cd}.
     * If {@code query} is blank, no FTS predicate is applied.
     *
     * <p><strong>Note:</strong> {@code Pageable.getSort()} is intentionally ignored.
     * Ordering is hard-coded to {@code (rank DESC, id ASC)} when the query is non-blank,
     * otherwise {@code (title ASC, id ASC)}. Whitelist-based sort support is added at
     * the REST layer in Task 05.
     */
    Page<BookSearchProjection> search(String query, FacetFilter filter, Pageable pageable);

    /**
     * Aggregate facet counts for the same query/filter combination, broken
     * down by lang, year, and genre.
     */
    FacetCounts facetCounts(String query, FacetFilter filter);
}
