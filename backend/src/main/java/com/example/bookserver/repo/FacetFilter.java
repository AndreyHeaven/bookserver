package com.example.bookserver.repo;

import java.util.List;

/**
 * Filter applied on top of a FTS query when searching books.
 * All fields are optional; {@code null} (or empty list for {@code genreIds})
 * means "no filter on this dimension".
 */
public record FacetFilter(String lang, Integer year, List<Long> genreIds) {

    public static FacetFilter empty() {
        return new FacetFilter(null, null, List.of());
    }
}
