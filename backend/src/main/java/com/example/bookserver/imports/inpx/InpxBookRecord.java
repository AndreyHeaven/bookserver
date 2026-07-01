package com.example.bookserver.imports.inpx;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;

import java.util.List;

public record InpxBookRecord(List<ImportedAuthor> authors,
                             List<String> genres,
                             String title,
                             ImportedSeries series,
                             String archive,
                             Long size,
                             String libId,
                             boolean deleted,
                             String extension,
                             String lang,
                             String keywords) {

    public String zipEntryName() {
        return libId + "." + extension;
    }
}
