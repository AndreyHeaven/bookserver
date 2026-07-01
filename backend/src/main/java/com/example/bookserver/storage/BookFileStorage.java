package com.example.bookserver.storage;

import java.io.IOException;
import java.io.InputStream;

public interface BookFileStorage {

    StoredFile store(InputStream input, String fileName) throws IOException;

    InputStream open(String path) throws IOException;

    void delete(String path) throws IOException;
}
