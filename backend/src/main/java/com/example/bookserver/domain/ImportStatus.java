package com.example.bookserver.domain;

/** Status of an asynchronous import job. */
public enum ImportStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
