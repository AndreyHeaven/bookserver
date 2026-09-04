package com.example.bookserver.telegram;

sealed interface TelegramAction permits TelegramAction.SearchPage, TelegramAction.Details, TelegramAction.Download {

    long telegramUid();

    record SearchPage(long telegramUid, String query, int page) implements TelegramAction {
    }

    record Details(long telegramUid, long bookId) implements TelegramAction {
    }

    record Download(long telegramUid, long bookId, long fileId) implements TelegramAction {
    }
}
