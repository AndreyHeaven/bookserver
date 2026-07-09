package com.example.bookserver.books.dto;

import java.util.List;

public record BookDetailsDto(Long id,
                             String title,
                             List<PersonBriefDto> authors,
                             List<PersonBriefDto> translators,
                             Integer year,
                             String lang,
                             String fileType,
                             Long fileSize,
                             String annotation,
                             List<GenreBriefDto> genres,
                             List<BookSeriesDto> series,
                             List<BookFileDto> files,
                             String coverUrl) {
}
