package com.example.bookserver.telegram;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class TelegramApiClient {

    private final RestClient restClient;

    public TelegramApiClient(TelegramProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + properties.botToken())
                .build();
    }

    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        TelegramUpdatesResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", timeoutSeconds)
                        .build())
                .retrieve()
                .body(TelegramUpdatesResponse.class);
        return response == null || response.result() == null ? List.of() : response.result();
    }

    public void deleteWebhook() {
        restClient.post().uri("/deleteWebhook")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("drop_pending_updates", false))
                .retrieve()
                .toBodilessEntity();
    }

    public void sendMessage(long chatId, String text, List<List<TelegramInlineButton>> keyboard) {
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard", keyboard));
        restClient.post().uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void answerCallbackQuery(String callbackQueryId) {
        restClient.post().uri("/answerCallbackQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("callback_query_id", callbackQueryId))
                .retrieve()
                .toBodilessEntity();
    }

    public record TelegramInlineButton(String text, String callback_data) {
    }

    private record TelegramUpdatesResponse(boolean ok, List<TelegramUpdate> result) {
    }
}
