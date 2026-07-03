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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Fb2ImporterIT extends AbstractIntegrationTest {

    @Autowired ImportService importService;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void fb2_folder_imports_books_and_deduplicates_by_md5() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        insertGenre("fb2-sf-" + suffix, "FB2 Science Fiction");
        insertGenre("fb2-adv-" + suffix, "FB2 Adventure");
        Path source = TEST_IMPORTS_DIR.resolve("fb2-" + suffix);
        Files.createDirectories(source);
        Files.writeString(source.resolve("one.fb2"), fb2("Эхо ФБ2", "Иванов", "Иван", "Иванович",
                "fb2-sf-" + suffix, "ru", "2020", "Цикл", "1", "Аннотация один"), StandardCharsets.UTF_8);
        Files.writeString(source.resolve("two.fb2"), fb2("Второй ФБ2", "Петров", "Петр", "",
                "fb2-adv-" + suffix, "ru", "2021", "", "", "Аннотация два"), StandardCharsets.UTF_8);

        Long jobId = importService.startImport(new StartImportRequest("fb2-folder", source.toString(), Map.of())).id();
        awaitJob(jobId, ImportStatus.SUCCEEDED);

        assertThat(count("books")).isEqualTo(2);
        assertThat(count("persons")).isEqualTo(2);
        assertThat(count("book_files")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM series WHERE title = 'Цикл'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM annotations WHERE body LIKE 'Аннотация%'", Long.class)).isEqualTo(2);

        Long duplicateJobId = importService.startImport(new StartImportRequest("fb2-folder", source.toString(), Map.of())).id();
        awaitJob(duplicateJobId, ImportStatus.SUCCEEDED);
        assertThat(count("books")).as("same physical md5 must update existing books").isEqualTo(2);
    }

    @Test
    void fb2_folder_imports_books_packed_in_zip_archives() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        insertGenre("fb2-zip-" + suffix, "FB2 Zip Genre");
        Path source = TEST_IMPORTS_DIR.resolve("fb2zip-" + suffix);
        Files.createDirectories(source);
        Path archive = source.resolve("books.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            addZipEntry(zip, "a.fb2", fb2("Zip Один", "Зипов", "Зип", "",
                    "fb2-zip-" + suffix, "ru", "2020", "", "", "Zip аннотация один"));
            addZipEntry(zip, "b.fb2", fb2("Zip Два", "Архивов", "Арх", "",
                    "fb2-zip-" + suffix, "ru", "2021", "", "", "Zip аннотация два"));
        }

        Long jobId = importService.startImport(new StartImportRequest("fb2-folder", source.toString(), Map.of())).id();
        awaitJob(jobId, ImportStatus.SUCCEEDED);

        assertThat(count("books")).isEqualTo(2);
        assertThat(count("book_files")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT storage_path) FROM book_files", Long.class))
                .as("the archive is stored once and shared by both entries").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_files WHERE entry_name IN ('a.fb2', 'b.fb2')", Long.class))
                .isEqualTo(2);
    }

    @Test
    void import_source_outside_base_dir_is_rejected() {
        assertThatThrownBy(() -> importService.startImport(new StartImportRequest(
                "fb2-folder", TEST_IMPORTS_DIR.getParent().resolve("outside").toString(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside");
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

    private void insertGenre(String code, String title) {
        jdbc.update("INSERT INTO genres(code, title, meta_section, position) VALUES (?, ?, 'test', 0)", code, title);
    }

    private static void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static String fb2(String title,
                              String lastName,
                              String firstName,
                              String middleName,
                              String genre,
                              String lang,
                              String year,
                              String sequence,
                              String number,
                              String annotation) {
        String seq = sequence.isBlank() ? "" : "<sequence name=\"" + sequence + "\" number=\"" + number + "\"/>";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <genre>%s</genre>
                      <author>
                        <first-name>%s</first-name>
                        <middle-name>%s</middle-name>
                        <last-name>%s</last-name>
                      </author>
                      <book-title>%s</book-title>
                      <annotation><p>%s</p></annotation>
                      <date>%s</date>
                      <lang>%s</lang>
                      %s
                    </title-info>
                  </description>
                  <body><section><p>Body is intentionally ignored.</p></section></body>
                </FictionBook>
                """.formatted(genre, firstName, middleName, lastName, title, annotation, year, lang, seq);
    }
}
