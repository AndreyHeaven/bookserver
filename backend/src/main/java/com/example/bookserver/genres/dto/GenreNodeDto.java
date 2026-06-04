package com.example.bookserver.genres.dto;

import java.util.List;

public record GenreNodeDto(Long id,
                           String code,
                           String title,
                           String metaSection,
                           long bookCount,
                           List<GenreNodeDto> children) {
}
