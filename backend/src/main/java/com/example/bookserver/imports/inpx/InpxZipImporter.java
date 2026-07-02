package com.example.bookserver.imports.inpx;

import com.example.bookserver.imports.BookImporter;
import com.example.bookserver.imports.ImportContext;
import com.example.bookserver.imports.ImportJobProgress;
import com.example.bookserver.imports.ImportedBook;
import com.example.bookserver.imports.ImportedBookWriter;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class InpxZipImporter implements BookImporter {

    private static final Logger log = LoggerFactory.getLogger(InpxZipImporter.class);

    private static final String INPX_SUFFIX = ".inpx";
    private static final String ZIP_SUFFIX = ".zip";

    private final InpxParser parser;
    private final BookFileStorage storage;
    private final ImportedBookWriter writer;

    public InpxZipImporter(InpxParser parser, BookFileStorage storage, ImportedBookWriter writer) {
        this.parser = parser;
        this.storage = storage;
        this.writer = writer;
    }

    @Override
    public String type() {
        return "inpx-zip";
    }

    @Override
    public String description() {
        return "Imports INPX metadata and zipped FB2 files";
    }

    @Override
    public void importFrom(ImportContext context, ImportJobProgress progress) throws Exception {
        if (!Files.isDirectory(context.sourcePath())) {
            throw new IllegalArgumentException("INPX source must be a directory: " + context.sourcePath());
        }
        List<Path> inpxFiles = listFiles(context.sourcePath(), name -> name.endsWith(INPX_SUFFIX));
        List<Path> zipFiles = listFiles(context.sourcePath(), name -> name.endsWith(ZIP_SUFFIX));
        if (inpxFiles.isEmpty()) {
            throw new IllegalArgumentException("No .inpx files found in " + context.sourcePath());
        }
        if (zipFiles.isEmpty()) {
            throw new IllegalArgumentException("No book .zip archives found in " + context.sourcePath());
        }

        List<InpxBookRecord> records = parseRecords(inpxFiles);
        List<RangedArchive> archives = zipFiles.stream().map(RangedArchive::from).toList();
        Map<Path, List<InpxBookRecord>> recordsByArchive = groupByArchive(records, archives);

        long total = records.size();
        long processed = 0;
        progress.update(processed, total);

        String catalog = inpxFiles.get(0).getFileName().toString();
        // Iterate archive-by-archive so every ZIP is opened and read exactly once.
        for (Map.Entry<Path, List<InpxBookRecord>> archiveRecords : recordsByArchive.entrySet()) {
            processed = importArchive(archiveRecords.getKey(), archiveRecords.getValue(),
                    catalog, progress, processed, total);
        }
    }

    private long importArchive(Path archivePath, List<InpxBookRecord> archiveRecords, String catalog,
                               ImportJobProgress progress, long processed, long total) throws Exception {
        Map<String, InpxBookRecord> byEntryName = archiveRecords.stream()
                .collect(Collectors.toMap(
                        record -> record.zipEntryName().toLowerCase(Locale.ROOT),
                        record -> record,
                        (first, second) -> first,
                        LinkedHashMap::new));
        String archiveName = archivePath.getFileName().toString();
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                InpxBookRecord record = byEntryName.remove(entryFileName(entry.getName()));
                if (record == null) {
                    continue;
                }
                StoredFile stored;
                try (var input = zip.getInputStream(entry)) {
                    stored = storage.store(input, entry.getName());
                }
                writer.write(new ImportedBook(
                        record.title(),
                        record.authors(),
                        record.genres(),
                        record.series(),
                        record.lang(),
                        null,
                        record.keywords(),
                        null,
                        record.extension().toLowerCase(Locale.ROOT),
                        archiveName,
                        catalog,
                        stored));
                progress.update(++processed, total);
            }
        }
        if (!byEntryName.isEmpty()) {
            log.warn("{} book file(s) listed in INPX were not found inside archive {}: {}",
                    byEntryName.size(), archiveName, byEntryName.keySet());
        }
        return processed;
    }

    private List<InpxBookRecord> parseRecords(List<Path> inpxFiles) {
        return inpxFiles.stream()
                .flatMap(path -> {
                    try {
                        return parser.parse(path).stream();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(record -> !record.deleted())
                .toList();
    }

    /**
     * Assigns every INPX record to the ZIP archive that stores it. The record is matched either by an explicit
     * archive name coming from the INPX, or by its numeric {@code libId} falling into the archive's file-name range
     * (e.g. {@code d.fb2-009373-367300.zip} holds ids from 009373 to 367300).
     */
    private Map<Path, List<InpxBookRecord>> groupByArchive(List<InpxBookRecord> records, List<RangedArchive> archives) {
        Map<String, Path> byFileName = archives.stream()
                .collect(Collectors.toMap(
                        archive -> archive.path().getFileName().toString().toLowerCase(Locale.ROOT),
                        RangedArchive::path,
                        (first, second) -> first));

        Map<Path, List<InpxBookRecord>> result = new LinkedHashMap<>();
        List<InpxBookRecord> unresolved = new ArrayList<>();
        for (InpxBookRecord record : records) {
            Path archive = resolveArchive(record, archives, byFileName);
            if (archive == null) {
                unresolved.add(record);
                continue;
            }
            result.computeIfAbsent(archive, key -> new ArrayList<>()).add(record);
        }
        if (!unresolved.isEmpty()) {
            log.warn("Could not map {} INPX record(s) to any ZIP archive; they will be skipped", unresolved.size());
        }
        return result;
    }

    private Path resolveArchive(InpxBookRecord record, List<RangedArchive> archives, Map<String, Path> byFileName) {
        if (record.archive() != null) {
            String name = record.archive().toLowerCase(Locale.ROOT);
            Path direct = byFileName.get(name);
            if (direct == null) {
                direct = byFileName.get(name + ZIP_SUFFIX);
            }
            if (direct != null) {
                return direct;
            }
        }
        Long libId = parseLibId(record.libId());
        if (libId != null) {
            for (RangedArchive archive : archives) {
                if (archive.contains(libId)) {
                    return archive.path();
                }
            }
        }
        return null;
    }

    private List<Path> listFiles(Path directory, Predicate<String> nameFilter) throws Exception {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> nameFilter.test(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static String entryFileName(String name) {
        int slash = name.lastIndexOf('/');
        return name.substring(slash + 1).toLowerCase(Locale.ROOT);
    }

    private static Long parseLibId(String libId) {
        if (libId == null || libId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(libId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A book ZIP archive whose file name encodes the inclusive range of {@code libId}s it contains,
     * e.g. {@code d.fb2-009373-367300.zip}. When the name has no range, {@link #contains(long)} is always false.
     */
    private record RangedArchive(Path path, long start, long end) {

        private static final Pattern RANGE = Pattern.compile("(\\d+)-(\\d+)\\.zip$", Pattern.CASE_INSENSITIVE);

        static RangedArchive from(Path path) {
            Matcher matcher = RANGE.matcher(path.getFileName().toString());
            if (matcher.find()) {
                long start = Long.parseLong(matcher.group(1));
                long end = Long.parseLong(matcher.group(2));
                return new RangedArchive(path, Math.min(start, end), Math.max(start, end));
            }
            return new RangedArchive(path, Long.MIN_VALUE, Long.MIN_VALUE);
        }

        boolean contains(long libId) {
            return start != Long.MIN_VALUE && libId >= start && libId <= end;
        }
    }
}
