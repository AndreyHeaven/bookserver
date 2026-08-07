package com.example.bookserver.books.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record BookSearchResponse(List<BookCardDto> content,
                                 long totalElements,
                                 int totalPages,
                                 int page,
                                 int size) {

    public static BookSearchResponse of(Page<BookCardDto> page) {
        return new BookSearchResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
