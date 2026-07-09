package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;

import java.util.List;

public record Fb2Metadata(String title,
                          List<ImportedAuthor> authors,
                          List<String> genres,
                          ImportedSeries series,
                          String lang,
                          Integer year,
                          String annotation,
                          byte[] coverImage,
                          String coverContentType) {
}
