package com.example.bookserver;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common base for Spring Boot integration tests. Boots an ephemeral
 * PostgreSQL 16 container, wires the datasource to it, and runs all
 * Liquibase changelogs automatically on context startup. Subclasses
 * should add their own slice annotations (e.g. {@code @AutoConfigureMockMvc}).
 *
 * <p>Provides a per-test {@link #truncateAll()} that resets all business
 * tables (but preserves the role seed) — wired as {@code @BeforeEach} so
 * each test sees a clean DB.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource") // container lifecycle managed by Testcontainers
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("bookserver")
            .withUsername("bookserver")
            .withPassword("bookserver");

    protected static final Path TEST_IMPORTS_DIR = createTempDir("bookserver-imports-");
    protected static final Path TEST_BOOKS_DIR = createTempDir("bookserver-books-");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("app.imports.base-dir", TEST_IMPORTS_DIR::toString);
        registry.add("app.storage.books-dir", TEST_BOOKS_DIR::toString);
    }

    private static Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Business tables to truncate before each test for isolation.
     *
     * <p>Order is irrelevant because we issue one
     * {@code TRUNCATE ... CASCADE RESTART IDENTITY} statement.
     *
     * <p>Intentionally excluded:
     * <ul>
     *   <li>{@code roles} — Liquibase seed of {@code ROLE_USER} / {@code ROLE_ADMIN}
     *       required by {@code AuthService.register()}.</li>
     *   <li>{@code genres} — Liquibase seed of 272 genres (changeset 006-001).</li>
     *   <li>{@code databasechangelog}, {@code databasechangeloglock} — managed by Liquibase itself.</li>
     * </ul>
     */
    private static final String[] TABLES_TO_TRUNCATE = {
            "annotations",
            "book_authors",
            "book_translators",
            "book_genres",
            "book_series_members",
            "book_list_items",
            "book_list_shares",
            "book_lists",
            "conversion_jobs",
            "import_jobs",
            "book_files",
            "books",
            "persons",
            "series",
            "user_roles",
            "users"
    };

    @BeforeEach
    void cleanDb() {
        truncateAll();
    }

    /**
     * Resets all business tables via a single {@code TRUNCATE … RESTART IDENTITY CASCADE}
     * so that auto-generated IDs and FK chains are all cleared. {@code roles} is preserved.
     */
    protected void truncateAll() {
        if (jdbcTemplate == null) {
            return;
        }
        String tables = String.join(", ", TABLES_TO_TRUNCATE);
        jdbcTemplate.execute("TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE");
    }
}
