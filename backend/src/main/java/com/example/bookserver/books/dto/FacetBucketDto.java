package com.example.bookserver.books.dto;

public record FacetBucketDto<T>(T value, long count) {
}
