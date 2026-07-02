package com.example.bookserver.imports.inpx;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

@Component
public class InpxParser {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public List<InpxBookRecord> parse(Path inpxFile) throws Exception {
        List<InpxBookRecord> records = new ArrayList<>();
        try (ZipFile zip = new ZipFile(inpxFile.toFile(), CHARSET)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".inp")) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), CHARSET))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            records.add(parseLine(line));
                        }
                    }
                }
            }
        }
        return records;
    }

    private InpxBookRecord parseLine(String line) {
        String[] f = line.split(String.copyValueOf(new char[]{(char) 4}), -1);
        if (f.length < 10) {
            throw new IllegalArgumentException("INPX line has fewer than 10 fields");
        }
        String ext = value(f, 9);
        return new InpxBookRecord(
                parseAuthors(value(f, 0)),
                splitList(value(f, 1)),
                value(f, 2),
                new ImportedSeries(blankToNull(value(f, 3)), parseInteger(value(f, 4))),
                value(f, 5),
                parseLong(value(f, 6)),
                value(f, 7),
                "1".equals(value(f, 8)),
                ext == null || ext.isBlank() ? "fb2" : ext,
                value(f, 11),
                value(f, 13));
    }

    private static List<ImportedAuthor> parseAuthors(String value) {
        List<ImportedAuthor> out = new ArrayList<>();
        for (String raw : splitList(value)) {
            String[] parts = raw.split(",", -1);
            out.add(new ImportedAuthor(value(parts, 0), value(parts, 1), value(parts, 2)));
        }
        return out;
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : value.split(":")) {
            if (!item.isBlank()) {
                out.add(item.trim());
            }
        }
        return out;
    }

    private static String value(String[] values, int index) {
        if (index >= values.length) {
            return null;
        }
        return blankToNull(values[index]);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
