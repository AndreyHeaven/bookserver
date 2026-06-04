package com.example.bookserver.authors.dto;

public record AuthorDetailsDto(Long id,
                               String lastName,
                               String firstName,
                               String middleName,
                               String fullName,
                               long bookCount) {
}
