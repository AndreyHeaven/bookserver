package com.example.bookserver.lists.dto;

import java.util.List;

public record PublicBookListDto(String title,
                                String description,
                                List<BookListItemDto> items) {
}
