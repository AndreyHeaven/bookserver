package com.example.bookserver.repo;

/**
 * Lightweight DTO returned by {@link BookSearchRepository#search}.
 * Mapped manually from a native-SQL result row.
 */
public record BookSearchProjection(
        Long id,
        String title,
        Integer year,
        String lang,
        String fileType,
        Double rank) {
}
