package com.example.bookserver.lists.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBookListRequest(@NotBlank @Size(max = 255) String title,
                                    String description) {
}
