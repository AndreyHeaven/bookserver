package com.example.bookserver.storage;

public record StoredFile(String path, long size, String md5) {
}
