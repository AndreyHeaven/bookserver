package com.example.bookserver.telegram;


public record TelegramCallbackQuery(
        String id,
        TelegramUser from,
        TelegramMessage message,
        String data) {
}
