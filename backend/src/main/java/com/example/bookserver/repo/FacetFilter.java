package com.example.bookserver.repo;

import java.util.List;

/**
 * Filter applied on top of a FTS query when searching books.
 * All fields are optional; {@code null} (or empty list for {@code genreIds})
 * means "no filter on this dimension".
 */
public record FacetFilter(String lang,
                          Integer year,
                          List<Long> genreIds,
                          Integer yearFrom,
                          Integer yearTo,
                          Long authorId) {

    public FacetFilter(String lang, Integer year, List<Long> genreIds) {
        this(lang, year, genreIds, null, null, null);
    }

    public FacetFilter(String lang, Integer yearFrom, Integer yearTo, List<Long> genreIds, Long authorId) {
        this(lang, null, genreIds, yearFrom, yearTo, authorId);
    }

    public static FacetFilter empty() {
        return new FacetFilter(null, null, List.of(), null, null, null);
    }
}
