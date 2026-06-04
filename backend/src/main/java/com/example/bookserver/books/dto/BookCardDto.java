package com.example.bookserver.books.dto;

import java.util.List;

public record BookCardDto(Long id,
                          String title,
                          List<PersonBriefDto> authors,
                          Integer year,
                          String lang,
                          String fileType,
                          boolean hasFiles,
                          String coverUrl) {
}
