package com.example.bookserver.books;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.auth.dto.LoginRequest;
import com.example.bookserver.auth.dto.RegisterRequest;
import com.example.bookserver.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookSearchControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void books_endpoints_require_auth() throws Exception {
        mockMvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/books/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/books/facets")).andExpect(status().isUnauthorized());
    }

    @Test
    void search_details_and_facets_work_with_fts_and_recursive_genre_filter() throws Exception {
        Fixture f = seedFixture("books");
        String token = token();

        mockMvc.perform(get("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "эхо")
                        .param("genre_id", f.parentGenreId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Эхо далекой звезды"))
                .andExpect(jsonPath("$.content[0].authors[0].fullName").value("Толстой Алексей Николаевич"))
                .andExpect(jsonPath("$.facets.langs[0].value").value("ru"));

        mockMvc.perform(get("/api/books/{id}", f.echoBookId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Эхо далекой звезды"))
                .andExpect(jsonPath("$.annotation").value("Аннотация к эху"))
                .andExpect(jsonPath("$.files[0].format").value("fb2"))
                .andExpect(jsonPath("$.genres[0].path[0]").value("Фантастика"));

        mockMvc.perform(get("/api/books/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/books/facets")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "эхо"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.langs[0].value").value("ru"))
                .andExpect(jsonPath("$.years[0].value").value(2020))
                .andExpect(jsonPath("$.genres[0].count").value(1));
    }

    private Fixture seedFixture(String prefix) {
        String suffix = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long parent = insertGenre("test-parent-" + suffix, "Фантастика", null);
        Long child = insertGenre("test-child-" + suffix, "Научная фантастика", parent);
        Long author = insertPerson("Толстой", "Алексей", "Николаевич");
        Long book = insertBook("Эхо далекой звезды", "ru", 2020, "космос приключения");
        jdbc.update("INSERT INTO book_authors(book_id, person_id, position) VALUES (?, ?, 0)", book, author);
        jdbc.update("INSERT INTO book_genres(book_id, genre_id) VALUES (?, ?)", book, child);
        jdbc.update("INSERT INTO annotations(book_id, body) VALUES (?, ?)", book, "Аннотация к эху");
        jdbc.update("INSERT INTO book_files(book_id, format, storage_path, size_bytes) VALUES (?, 'fb2', ?, 1234)",
                book, "/tmp/" + suffix + ".fb2");
        insertBook("Английская книга", "en", 2021, "plain");
        return new Fixture(parent, child, author, book);
    }

    private Long insertGenre(String code, String title, Long parentId) {
        return jdbc.queryForObject(
                "INSERT INTO genres(code, title, meta_section, parent_id, position) VALUES (?, ?, 'test', ?, 0) RETURNING id",
                Long.class, code, title, parentId);
    }

    private Long insertPerson(String lastName, String firstName, String middleName) {
        return jdbc.queryForObject(
                "INSERT INTO persons(last_name, first_name, middle_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, lastName, firstName, middleName);
    }

    private Long insertBook(String title, String lang, int year, String keywords) {
        return jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type, keywords) VALUES (?, ?, ?, 'fb2', ?) RETURNING id",
                Long.class, title, lang, year, keywords);
    }

    private String token() throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, username + "@e.test", "password1"))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class).accessToken();
    }

    private record Fixture(Long parentGenreId, Long childGenreId, Long authorId, Long echoBookId) {
    }
}
