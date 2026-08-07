package com.example.bookserver.auth;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.auth.dto.LoginRequest;
import com.example.bookserver.auth.dto.RefreshRequest;
import com.example.bookserver.auth.dto.RegisterRequest;
import com.example.bookserver.auth.dto.TokenResponse;
import com.example.bookserver.domain.UserEntity;
import com.example.bookserver.repo.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_returns_201_and_persists_user() throws Exception {
        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));

        assertThat(userRepository.findByUsername("alice")).isPresent();
    }

    @Test
    void register_validation_returns_400() throws Exception {
        RegisterRequest req = new RegisterRequest("", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void login_with_correct_password_returns_tokens() throws Exception {
        registerUser("bob", "bob@example.com", "password1");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("bob", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        TokenResponse tokens = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void login_with_wrong_password_returns_401() throws Exception {
        registerUser("carol", "carol@example.com", "password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("carol", "wrong-pw"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_with_valid_access_token_returns_200() throws Exception {
        registerUser("dave", "dave@example.com", "password1");
        TokenResponse tokens = login("dave", "password1");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dave"))
                .andExpect(jsonPath("$.email").value("dave@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void refresh_with_valid_refresh_token_returns_new_tokens() throws Exception {
        registerUser("eve", "eve@example.com", "password1");
        TokenResponse tokens = login("eve", "password1");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refresh_with_access_token_is_rejected() throws Exception {
        registerUser("frank", "frank@example.com", "password1");
        TokenResponse tokens = login("frank", "password1");

        // Passing an access token where a refresh token is expected must fail with 401.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.accessToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuator_health_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void register_duplicate_username_returns_409() throws Exception {
        // First register succeeds.
        registerUser("dup1", "a@example.com", "password1");

        // Second register with the same username (different email) must hit the unique
        // constraint on users.username and be translated by GlobalExceptionHandler to 409.
        RegisterRequest duplicate = new RegisterRequest("dup1", "b@example.com", "password1");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_with_disabled_user_returns_401() throws Exception {
        registerUser("disabled1", "disabled1@example.com", "password1");

        UserEntity user = userRepository.findByUsername("disabled1").orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("disabled1", "password1"))))
                .andExpect(status().isUnauthorized());
    }

    private void registerUser(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());
    }

    private TokenResponse login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }
}
