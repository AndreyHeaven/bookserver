package com.example.bookserver.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Receives updates via long polling. The library owns the polling loop, the update
 * offset, the {@code deleteWebhook} call on startup and the exponential backoff on
 * failures, so none of that is reimplemented here.
 */
@Service
public class TelegramLongPollingService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingService.class);
    /** Seconds Telegram holds an empty getUpdates call open before answering. */
    private static final int POLLING_TIMEOUT_SECONDS = 30;

    private final TelegramBotsLongPollingApplication pollingApplication = new TelegramBotsLongPollingApplication();
    private final TelegramBotService botService;
    private final TelegramProperties properties;
    private final TelegramUrl telegramUrl;

    public TelegramLongPollingService(TelegramBotService botService,
                                      TelegramProperties properties,
                                      TelegramUrl telegramUrl) {
        this.botService = botService;
        this.properties = properties;
        this.telegramUrl = telegramUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled()) {
            return;
        }
        try {
            pollingApplication.registerBot(
                    properties.botToken(),
                    () -> telegramUrl,
                    this::getUpdates,
                    (LongPollingSingleThreadUpdateConsumer) botService::handle);
        } catch (TelegramApiException exception) {
            log.error("Failed to start Telegram long polling", exception);
        }
    }

    private GetUpdates getUpdates(int offset) {
        return GetUpdates.builder()
                .offset(offset)
                .timeout(POLLING_TIMEOUT_SECONDS)
                .build();
    }

    @Override
    public void close() throws Exception {
        pollingApplication.close();
    }
}
