package com.example.bookserver.imports;

public interface ImportJobProgress {

    void update(long processed, long total);

    void warning(String message);

    void error(String message);
}
