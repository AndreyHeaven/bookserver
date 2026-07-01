package com.example.bookserver.lists;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookListsControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void lists_endpoints_require_auth() throws Exception {
        mockMvc.perform(get("/api/lists")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lists")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_add_items_and_share_returns_token_url_and_qr() throws Exception {
        String token = token();
        Long book1 = insertBook("Первая книга");
        Long book2 = insertBook("Вторая книга");

        Long listId = createList(token, "Моя подборка", "описание");

        addItem(token, listId, book1);
        addItem(token, listId, book2);

        mockMvc.perform(get("/api/lists/{id}", listId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Моя подборка"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Первая книга"));

        mockMvc.perform(post("/api/lists/{id}/share", listId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.publicUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/public/lists/")))
                .andExpect(jsonPath("$.qrPngBase64").isNotEmpty());
    }

    @Test
    void non_owner_cannot_access_others_list() throws Exception {
        String owner = token();
        Long listId = createList(owner, "Приватная", null);

        String intruder = token();
        mockMvc.perform(get("/api/lists/{id}", listId).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/lists/{id}", listId).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isForbidden());
    }

    private Long createList(String token, String title, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":" + (description == null ? "null" : "\"" + description + "\"") + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private void addItem(String token, Long listId, Long bookId) throws Exception {
        mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + "}"))
                .andExpect(status().isOk());
    }

    private Long insertBook(String title) {
        return jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type) VALUES (?, 'ru', 2020, 'fb2') RETURNING id",
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
}
