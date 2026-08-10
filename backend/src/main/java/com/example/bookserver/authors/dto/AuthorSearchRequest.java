package com.example.bookserver.authors.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record AuthorSearchRequest(String q,
                                  @Pattern(regexp = "^[\\p{L}]+$") String letter,
                                  @Min(0) Integer page,
                                  @Min(1) @Max(100) Integer size,
                                  String sort) {
}
