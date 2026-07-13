package com.example.bookserver.imports;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.books.BookDownloadService;
import com.example.bookserver.domain.ImportStatus;
import com.example.bookserver.imports.dto.StartImportRequest;
import com.example.bookserver.repo.ImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code in-place} storage mode: imported files are referenced where they
 * already live (inside imports.base-dir) instead of being copied into books-dir, and are
 * still downloadable through the normal pipeline.
 */
@TestPropertySource(properties = "app.imports.storage-mode=in-place")
class InPlaceImportIT extends AbstractIntegrationTest {

    @Autowired ImportService importService;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired BookDownloadService downloadService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void fb2_standalone_is_referenced_in_place_not_copied() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        insertGenre("inplace-" + suffix, "In-place Genre");
        Path source = TEST_IMPORTS_DIR.resolve("inplace-" + suffix);
        Files.createDirectories(source);
        Path original = source.resolve("book.fb2");
        String content = fb2("In-place книга", "inplace-" + suffix);
        Files.writeString(original, content, StandardCharsets.UTF_8);

        Long jobId = importService.startImport(
                new StartImportRequest("fb2-folder", source.toString(), Map.of())).id();
        awaitJob(jobId, ImportStatus.SUCCEEDED);

        assertThat(count("books")).isEqualTo(1);
        assertThat(count("book_files")).isEqualTo(1);

        // In copy mode this would be a relative shard path (e.g. ab/<md5>.fb2); an absolute path
        // equal to the untouched original proves the file was referenced in place, not copied.
        String storagePath = jdbc.queryForObject("SELECT storage_path FROM book_files", String.class);
        assertThat(storagePath)
                .as("in-place file must point at the untouched original")
                .isEqualTo(original.toAbsolutePath().normalize().toString());

        // Download must still work by resolving the absolute in-place path.
        Long bookId = jdbc.queryForObject("SELECT id FROM books", Long.class);
        Long fileId = jdbc.queryForObject("SELECT id FROM book_files", Long.class);
        BookDownloadService.BookFileDownload download = downloadService.prepare(bookId, fileId);
        try (InputStream in = download.resource().getInputStream()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    private void awaitJob(Long jobId, ImportStatus status) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (importJobRepository.findById(jobId).orElseThrow().getStatus() == status) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(importJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(status);
    }

    private void insertGenre(String code, String title) {
        jdbc.update("INSERT INTO genres(code, title, meta_section, position) VALUES (?, ?, 'test', 0)", code, title);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static String fb2(String title, String genre) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <genre>%s</genre>
                      <author>
                        <first-name>Иван</first-name>
                        <last-name>Иванов</last-name>
                      </author>
                      <book-title>%s</book-title>
                      <annotation><p>In-place аннотация</p></annotation>
                      <date>2020</date>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                  <body><section><p>Body is intentionally ignored.</p></section></body>
                </FictionBook>
                """.formatted(genre, title);
    }
}
