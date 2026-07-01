package com.example.bookserver.lists.dto;

import com.example.bookserver.books.dto.PersonBriefDto;

import java.util.List;

public record BookListItemDto(Long bookId,
                              String title,
                              List<PersonBriefDto> authors,
                              Integer year,
                              int position) {
}
