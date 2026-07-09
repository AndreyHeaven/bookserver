package com.example.bookserver.repo;

import java.util.List;

/**
 * Filter applied on top of a FTS query when searching books.
 * All fields are optional; {@code null} (or an empty list for {@code langs}
 * / {@code genreIds}) means "no filter on this dimension".
 */
public record FacetFilter(List<String> langs,
                          Integer year,
                          List<Long> genreIds,
                          Integer yearFrom,
                          Integer yearTo,
                          Long authorId) {

    public FacetFilter {
        langs = langs == null ? List.of() : langs;
        genreIds = genreIds == null ? List.of() : genreIds;
    }

    public FacetFilter(List<String> langs, Integer year, List<Long> genreIds) {
        this(langs, year, genreIds, null, null, null);
    }

    public FacetFilter(List<String> langs, Integer yearFrom, Integer yearTo, List<Long> genreIds, Long authorId) {
        this(langs, null, genreIds, yearFrom, yearTo, authorId);
    }

    public static FacetFilter empty() {
        return new FacetFilter(List.of(), null, List.of(), null, null, null);
    }
}
