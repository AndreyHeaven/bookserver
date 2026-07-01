package com.example.bookserver.imports;

import java.nio.file.Path;
import java.util.Map;

public record ImportContext(Long jobId, String type, Path sourcePath, Map<String, String> options) {
}
