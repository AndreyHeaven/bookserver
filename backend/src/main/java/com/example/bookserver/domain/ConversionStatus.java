package com.example.bookserver.domain;

/** Status of an asynchronous format-conversion job. */
public enum ConversionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
