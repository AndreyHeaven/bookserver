package com.example.bookserver.imports;

import com.example.bookserver.storage.StoredFile;

import java.util.List;

public record ImportedBook(String title,
                           List<ImportedAuthor> authors,
                           List<String> genreCodes,
                           ImportedSeries series,
                           String lang,
                           Integer year,
                           String keywords,
                           String annotation,
                           String fileType,
                           String archiveName,
                           String inpxSource,
                           StoredFile storedFile,
                           String entryName,
                           byte[] coverImage,
                           String coverContentType) {
}
