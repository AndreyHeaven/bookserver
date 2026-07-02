package com.example.bookserver.authors;

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
class AuthorsControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void authors_endpoints_require_auth() throws Exception {
        mockMvc.perform(get("/api/authors")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/authors/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/authors/1/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void authors_search_letter_alphabet_and_books_work() throws Exception {
        Long authorId = seedAuthorBooks();
        String token = token();

        mockMvc.perform(get("/api/authors")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "Толстой"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(authorId))
                .andExpect(jsonPath("$.content[0].bookCount").value(2));

        mockMvc.perform(get("/api/authors")
                        .header("Authorization", "Bearer " + token)
                        .param("letter", "Т"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Толстой Алексей Николаевич"));

        mockMvc.perform(get("/api/authors/alphabet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].letter").value("Т"));

        mockMvc.perform(get("/api/authors/{id}/books", authorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].authors[0].fullName").value("Толстой Алексей Николаевич"));
    }

    private Long seedAuthorBooks() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long genre = jdbc.queryForObject(
                "INSERT INTO genres(code, title, meta_section, position) VALUES (?, 'Классика', 'test', 0) RETURNING id",
                Long.class, "author-genre-" + suffix);
        Long author = jdbc.queryForObject(
                "INSERT INTO persons(last_name, first_name, middle_name) VALUES ('Толстой', 'Алексей', 'Николаевич') RETURNING id",
                Long.class);
        Long b1 = insertBook("Аэлита", "ru", 1923);
        Long b2 = insertBook("Гиперболоид инженера Гарина", "ru", 1927);
        jdbc.update("INSERT INTO book_authors(book_id, person_id, position) VALUES (?, ?, 0)", b1, author);
        jdbc.update("INSERT INTO book_authors(book_id, person_id, position) VALUES (?, ?, 0)", b2, author);
        jdbc.update("INSERT INTO book_genres(book_id, genre_id) VALUES (?, ?)", b1, genre);
        jdbc.update("INSERT INTO book_genres(book_id, genre_id) VALUES (?, ?)", b2, genre);
        return author;
    }

    private Long insertBook(String title, String lang, int year) {
        return jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type) VALUES (?, ?, ?, 'fb2') RETURNING id",
                Long.class, title, lang, year);
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
}
