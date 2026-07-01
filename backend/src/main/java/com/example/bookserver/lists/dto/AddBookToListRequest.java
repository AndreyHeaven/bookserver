package com.example.bookserver.lists.dto;

import jakarta.validation.constraints.NotNull;

public record AddBookToListRequest(@NotNull Long bookId,
                                   Integer position) {
}
