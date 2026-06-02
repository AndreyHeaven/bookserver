package com.example.bookserver.repo;

import java.util.Map;

/**
 * Per-facet histograms of matching documents for a given FTS query + filter.
 */
public record FacetCounts(
        Map<String, Long> langs,
        Map<Integer, Long> years,
        Map<Long, Long> genres) {
}
