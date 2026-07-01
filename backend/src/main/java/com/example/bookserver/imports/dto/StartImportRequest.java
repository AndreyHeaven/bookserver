package com.example.bookserver.imports.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StartImportRequest(@NotBlank String type,
                                 @NotBlank String sourcePath,
                                 Map<String, String> options) {
}
