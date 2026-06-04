package com.example.bookserver.books.dto;

import java.util.List;

public record GenreBriefDto(Long id, String code, String title, List<String> path) {
}
