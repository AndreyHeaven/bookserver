package com.example.bookserver.imports;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.domain.ImportStatus;
import com.example.bookserver.imports.dto.StartImportRequest;
import com.example.bookserver.repo.ImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InpxImporterIT extends AbstractIntegrationTest {

    /** INP field separator: the 0x04 control character used by the INPX format. */
    private static final String INP_FIELD_SEPARATOR = "\u0004";

    @Autowired ImportService importService;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void inpx_zip_imports_books_and_deduplicates_by_md5() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        insertGenre("inpx-sf-" + suffix, "INPX Science Fiction");
        insertGenre("inpx-adv-" + suffix, "INPX Adventure");
        Path source = TEST_IMPORTS_DIR.resolve("inpx-" + suffix);
        Files.createDirectories(source);
        createBookArchive(source.resolve("sample-archive.zip"));
        createInpx(source.resolve("sample.inpx"), suffix);

        Long jobId = importService.startImport(new StartImportRequest("inpx-zip", source.toString(), Map.of())).id();
        awaitJob(jobId, ImportStatus.SUCCEEDED);

        assertThat(count("books")).isEqualTo(2);
        assertThat(count("persons")).isEqualTo(2);
        assertThat(count("book_genres")).isEqualTo(2);
        assertThat(count("book_files")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE archive_name = 'sample-archive.zip'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM series WHERE title = 'INPX Series'", Long.class))
                .isEqualTo(1);

        Long duplicateJobId = importService.startImport(new StartImportRequest("inpx-zip", source.toString(), Map.of())).id();
        awaitJob(duplicateJobId, ImportStatus.SUCCEEDED);
        assertThat(count("books")).isEqualTo(2);
    }

    private void awaitJob(Long jobId, ImportStatus status) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (importJobRepository.findById(jobId).orElseThrow().getStatus() == status) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(importJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(importJobRepository.findById(jobId).orElseThrow().getProcessedCount()).isEqualTo(2);
    }

    private void createBookArchive(Path zipPath) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
            addZipEntry(zip, "1001.fb2", "<FictionBook><body>one</body></FictionBook>");
            addZipEntry(zip, "1002.fb2", "<FictionBook><body>two</body></FictionBook>");
        }
    }

    private void createInpx(Path inpxPath, String suffix) throws Exception {
        // INP records separate fields with the 0x04 control character, not a printable delimiter.
        String records = String.join("\n",
                inpLine("Толстой,Алексей,Николаевич", "inpx-sf-" + suffix, "INPX Echo", "INPX Series", "1",
                        "sample-archive.zip", "41", "1001", "0", "fb2", "2020-01-01", "ru", "0", "space"),
                inpLine("Петров,Петр,Петрович", "inpx-adv-" + suffix, "INPX Road", "", " ",
                        "sample-archive.zip", "41", "1002", "0", "fb2", "2021-01-01", "ru", "0", "road"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(inpxPath), StandardCharsets.UTF_8)) {
            addZipEntry(zip, "records.inp", records);
        }
    }

    private static String inpLine(String... fields) {
        return String.join(INP_FIELD_SEPARATOR, fields);
    }

    private static void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void insertGenre(String code, String title) {
        jdbc.update("INSERT INTO genres(code, title, meta_section, position) VALUES (?, ?, 'test', 0)", code, title);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
