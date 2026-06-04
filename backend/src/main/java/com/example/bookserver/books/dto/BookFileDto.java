package com.example.bookserver.books.dto;

public record BookFileDto(Long id, String format, Long sizeBytes, String downloadUrl) {
}
