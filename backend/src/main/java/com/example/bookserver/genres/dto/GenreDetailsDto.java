package com.example.bookserver.genres.dto;

import java.util.List;

public record GenreDetailsDto(Long id,
                              String code,
                              String title,
                              String metaSection,
                              Long parentId,
                              String parentTitle,
                              List<GenreNodeDto> children,
                              long bookCount) {
}
