package com.example.bookserver.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        TelegramMessage message,
        @JsonProperty("callback_query") TelegramCallbackQuery callbackQuery) {
}
