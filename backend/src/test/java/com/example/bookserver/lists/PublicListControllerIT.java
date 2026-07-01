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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicListControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void shared_list_is_publicly_viewable_and_qr_is_png_then_revoke_returns_404() throws Exception {
        String token = token();
        Long book = insertBook("Публичная книга");
        Long listId = createList(token, "Публичный список");
        mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book + "}"))
                .andExpect(status().isOk());

        String shareToken = share(token, listId);

        // Public view without any Authorization header.
        mockMvc.perform(get("/api/public/lists/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Публичный список"))
                .andExpect(jsonPath("$.items[0].title").value("Публичная книга"));

        mockMvc.perform(get("/api/public/lists/{token}/qr.png", shareToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // Revoke → public endpoint returns 404.
        mockMvc.perform(delete("/api/lists/{id}/share", listId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/lists/{token}", shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void share_is_idempotent_unless_regenerate_requested() throws Exception {
        String token = token();
        Long listId = createList(token, "Список");

        String first = share(token, listId);
        String second = share(token, listId);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);

        MvcResult regen = mockMvc.perform(post("/api/lists/{id}/share", listId)
                        .header("Authorization", "Bearer " + token)
                        .param("regenerate", "true"))
                .andExpect(status().isOk())
                .andReturn();
        String regenerated = objectMapper.readTree(regen.getResponse().getContentAsString()).get("token").asText();
        org.assertj.core.api.Assertions.assertThat(regenerated).isNotEqualTo(first);
    }

    private String share(String token, Long listId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/lists/{id}/share", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private Long createList(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
