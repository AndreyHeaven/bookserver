package com.example.bookserver.genres;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.auth.dto.LoginRequest;
import com.example.bookserver.auth.dto.RegisterRequest;
import com.example.bookserver.auth.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
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
class GenresControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clearGenreCache() {
        if (cacheManager.getCache("genresTree") != null) {
            cacheManager.getCache("genresTree").clear();
        }
    }

    @Test
    void genres_endpoints_require_auth() throws Exception {
        mockMvc.perform(get("/api/genres/tree")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/genres/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/genres/1/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void tree_details_and_books_include_subgenres_flag_work() throws Exception {
        Fixture f = seedGenreBooks();
        String token = token();

        mockMvc.perform(get("/api/genres/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + f.parentGenreId() + ")].bookCount").value(2));

        mockMvc.perform(get("/api/genres/{id}", f.parentGenreId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Фантастика"))
                .andExpect(jsonPath("$.bookCount").value(2))
                .andExpect(jsonPath("$.children[0].title").value("Научная фантастика"));

        mockMvc.perform(get("/api/genres/{id}/books", f.parentGenreId())
                        .header("Authorization", "Bearer " + token)
                        .param("includeSubgenres", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/api/genres/{id}/books", f.parentGenreId())
                        .header("Authorization", "Bearer " + token)
                        .param("includeSubgenres", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private Fixture seedGenreBooks() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long parent = jdbc.queryForObject(
                "INSERT INTO genres(code, title, meta_section, position) VALUES (?, 'Фантастика', 'test', 0) RETURNING id",
                Long.class, "genre-parent-" + suffix);
        Long child = jdbc.queryForObject(
                "INSERT INTO genres(code, title, meta_section, parent_id, position) VALUES (?, 'Научная фантастика', 'test', ?, 0) RETURNING id",
                Long.class, "genre-child-" + suffix, parent);
        Long directBook = insertBook("Книга родителя");
        Long childBook = insertBook("Книга поджанра");
        jdbc.update("INSERT INTO book_genres(book_id, genre_id) VALUES (?, ?)", directBook, parent);
        jdbc.update("INSERT INTO book_genres(book_id, genre_id) VALUES (?, ?)", childBook, child);
        return new Fixture(parent, child);
    }

    private Long insertBook(String title) {
        return jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type) VALUES (?, 'ru', 2024, 'fb2') RETURNING id",
                Long.class, title);
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

    private record Fixture(Long parentGenreId, Long childGenreId) {
    }
}
