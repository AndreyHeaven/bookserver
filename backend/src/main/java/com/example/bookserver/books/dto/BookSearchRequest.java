package com.example.bookserver.books.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record BookSearchRequest(String q,
                                List<String> lang,
                                @Min(0) Integer yearFrom,
                                @Min(0) Integer yearTo,
                                List<Long> genreId,
                                Long authorId,
                                @Min(0) Integer page,
                                @Min(1) @Max(100) Integer size,
                                String sort) {
}
