package com.example.bookserver.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error body returned by {@link GlobalExceptionHandler}.
 *
 * @param timestamp   server time when the error was produced
 * @param status      HTTP status code
 * @param error       short status reason ("Bad Request", "Unauthorized", ...)
 * @param message     human-readable message
 * @param path        request URI that produced the error
 * @param fieldErrors optional list of per-field validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
