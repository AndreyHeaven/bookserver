package com.example.bookserver.telegram;

import com.example.bookserver.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link TelegramBotService} against a stand-in for api.telegram.org so that
 * the outgoing Bot API calls (their method, chat and inline keyboard) can be asserted
 * exactly as Telegram would receive them.
 *
 * <p>Updates are built from raw Telegram JSON — the same payloads the long polling
 * transport feeds in — to also cover the library's deserialization of our callbacks.
 */
class TelegramBotServiceIT extends AbstractIntegrationTest {

    /** Telegram account linked to the enabled user; the bot answers only this UID. */
    private static final long KNOWN_UID = 4242L;
    /** Telegram account not linked to any user; every request from it must be refused. */
    private static final long STRANGER_UID = 9999L;
    private static final long CHAT_ID = 55L;

    private static MockWebServer telegram;

    /** Parses fixtures with the library's own (Jackson 2) annotations, as long polling does. */
    private final ObjectMapper telegramJson = new ObjectMapper();

    @Autowired
    TelegramBotService botService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    static void startTelegram() throws IOException {
        telegram = new MockWebServer();
        telegram.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                                {"ok":true,"result":{"message_id":1,"date":1,
                                  "chat":{"id":55,"type":"private"},"text":"ok"}}
                                """);
            }
        });
        telegram.start();
    }

    @AfterAll
    static void stopTelegram() throws IOException {
        telegram.shutdown();
    }

    @DynamicPropertySource
    static void telegramProperties(DynamicPropertyRegistry registry) {
        registry.add("app.telegram.enabled", () -> true);
        registry.add("app.telegram.bot-token", () -> "test-token");
        registry.add("app.telegram.api-url", () -> telegram.url("/").toString());
        registry.add("app.telegram.public-base-url", () -> "https://books.test");
    }

    @BeforeEach
    void drainPendingRequests() throws InterruptedException {
        while (telegram.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS) != null) {
            // Discard traffic from previous tests so assertions see only their own calls.
        }
    }

    @Test
    void textMessageFromKnownUserRepliesWithBookKeyboard() throws Exception {
        seedUser(KNOWN_UID, true);
        seedBook("Эхо далекой звезды");

        botService.handle(messageUpdate(KNOWN_UID, "эхо"));

        JsonNode sent = nextCall("sendMessage");
        assertThat(sent.path("chat_id").asLong()).isEqualTo(CHAT_ID);
        assertThat(sent.path("text").asText()).contains("страница 1");
        JsonNode keyboard = sent.path("reply_markup").path("inline_keyboard");
        assertThat(keyboard).hasSize(1);
        assertThat(keyboard.get(0).get(0).path("text").asText()).isEqualTo("Эхо далекой звезды");
        assertThat(keyboard.get(0).get(0).path("callback_data").asText()).startsWith("tg:");
    }

    @Test
    void textMessageFromUnknownUserIsRefused() throws Exception {
        seedBook("Эхо далекой звезды");

        botService.handle(messageUpdate(STRANGER_UID, "эхо"));

        JsonNode sent = nextCall("sendMessage");
        assertThat(sent.path("text").asText()).isEqualTo("Доступ запрещён.");
        assertThat(sent.path("reply_markup").path("inline_keyboard")).isEmpty();
    }

    @Test
    void disabledUserIsRefused() throws Exception {
        seedUser(KNOWN_UID, false);
        seedBook("Эхо далекой звезды");

        botService.handle(messageUpdate(KNOWN_UID, "эхо"));

        assertThat(nextCall("sendMessage").path("text").asText()).isEqualTo("Доступ запрещён.");
    }

    @Test
    void callbackTokenIssuedForOneUserIsRejectedForAnother() throws Exception {
        seedUser(KNOWN_UID, true);
        seedUser(STRANGER_UID, true);
        seedBook("Эхо далекой звезды");

        botService.handle(messageUpdate(KNOWN_UID, "эхо"));
        String callbackData = nextCall("sendMessage")
                .path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asText();

        botService.handle(callbackUpdate(STRANGER_UID, callbackData));

        List<JsonNode> calls = nextCalls(2);
        assertThat(methodOf(calls)).contains("answercallbackquery");
        assertThat(textOf(calls)).contains("Ссылка истекла или доступ запрещён.");
    }

    @Test
    void unknownCallbackTokenIsRejected() throws Exception {
        seedUser(KNOWN_UID, true);

        botService.handle(callbackUpdate(KNOWN_UID, "tg:nonexistent-token"));

        assertThat(textOf(nextCalls(2))).contains("Ссылка истекла или доступ запрещён.");
    }

    @Test
    void downloadTokenIsSingleUse() throws Exception {
        seedUser(KNOWN_UID, true);
        long bookId = seedBook("Эхо далекой звезды");

        botService.handle(messageUpdate(KNOWN_UID, "эхо"));
        String detailsData = nextCall("sendMessage")
                .path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asText();

        botService.handle(callbackUpdate(KNOWN_UID, detailsData));
        String fileData = nextCalls(2).stream()
                .filter(call -> call.has("reply_markup"))
                .findFirst().orElseThrow()
                .path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asText();

        botService.handle(callbackUpdate(KNOWN_UID, fileData));
        String url = textOf(nextCalls(2)).stream()
                .filter(text -> text.startsWith("https://books.test"))
                .findFirst().orElseThrow();
        String token = url.substring(url.lastIndexOf('/') + 1);

        assertThat(botService.download(token)).isNotNull();
        assertThat(botService.download(token))
                .as("a download token must not be reusable")
                .isNull();
        assertThat(bookId).isPositive();
    }

    private JsonNode nextCall(String expectedMethod) throws Exception {
        RecordedRequest request = telegram.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).endsWith(expectedMethod.toLowerCase(java.util.Locale.ROOT));
        return telegramJson.readTree(request.getBody().readUtf8());
    }

    private List<JsonNode> nextCalls(int count) throws Exception {
        List<JsonNode> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RecordedRequest request = telegram.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            JsonNode body = telegramJson.readTree(request.getBody().readUtf8());
            ((com.fasterxml.jackson.databind.node.ObjectNode) body)
                    .put("_method", request.getPath().substring(request.getPath().lastIndexOf('/') + 1));
            calls.add(body);
        }
        return calls;
    }

    private static List<String> methodOf(List<JsonNode> calls) {
        return calls.stream().map(call -> call.path("_method").asText()).toList();
    }

    private static List<String> textOf(List<JsonNode> calls) {
        return calls.stream().map(call -> call.path("text").asText()).toList();
    }

    private Update messageUpdate(long telegramUid, String text) throws IOException {
        return telegramJson.readValue("""
                {"update_id":1,"message":{"message_id":1,"date":1,
                  "chat":{"id":%d,"type":"private"},
                  "from":{"id":%d,"is_bot":false,"first_name":"T"},
                  "text":"%s"}}
                """.formatted(CHAT_ID, telegramUid, text), Update.class);
    }

    private Update callbackUpdate(long telegramUid, String callbackData) throws IOException {
        return telegramJson.readValue("""
                {"update_id":2,"callback_query":{"id":"cb","chat_instance":"ci",
                  "from":{"id":%d,"is_bot":false,"first_name":"T"},
                  "data":"%s",
                  "message":{"message_id":2,"date":1,
                    "chat":{"id":%d,"type":"private"},
                    "from":{"id":%d,"is_bot":false,"first_name":"T"},"text":"x"}}}
                """.formatted(telegramUid, callbackData, CHAT_ID, telegramUid), Update.class);
    }

    private void seedUser(long telegramUid, boolean enabled) {
        String username = "tg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO users(username, email, password_hash, enabled, telegram_uid) "
                        + "VALUES (?, ?, 'x', ?, ?)",
                username, username + "@e.test", enabled, telegramUid);
    }

    private long seedBook(String title) throws IOException {
        Long bookId = jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type, keywords) "
                        + "VALUES (?, 'ru', 2020, 'fb2', 'эхо') RETURNING id",
                Long.class, title);
        // The download path streams the real file, so it has to exist on disk.
        // storage_path is stored relative to app.storage.books-dir.
        Path file = Files.createTempFile(TEST_BOOKS_DIR, "book-", ".fb2");
        Files.writeString(file, "<FictionBook/>");
        jdbc.update("INSERT INTO book_files(book_id, format, storage_path, size_bytes) "
                        + "VALUES (?, 'fb2', ?, ?)",
                bookId, TEST_BOOKS_DIR.relativize(file).toString(), Files.size(file));
        return bookId;
    }
}
