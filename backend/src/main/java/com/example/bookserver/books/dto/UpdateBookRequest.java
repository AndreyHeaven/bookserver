package com.example.bookserver.books.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBookRequest(
        @NotBlank @Size(max = 1024) String title,
        @Size(max = 8) String lang,
        Integer year,
        String annotation,
        String keywords) {
}
