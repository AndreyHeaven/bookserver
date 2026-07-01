package com.example.bookserver.lists.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BookListDto(Long id,
                          String title,
                          String description,
                          OffsetDateTime createdAt,
                          List<BookListItemDto> items) {
}
