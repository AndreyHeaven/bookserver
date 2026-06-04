package com.example.bookserver.authors.dto;

public record AuthorCardDto(Long id,
                            String lastName,
                            String firstName,
                            String middleName,
                            String fullName,
                            long bookCount) {
}
