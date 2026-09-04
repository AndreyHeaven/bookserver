package com.example.bookserver.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TelegramLongPollingService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingService.class);
    private final TelegramApiClient telegramApiClient;
    private final TelegramBotService botService;
    private final TelegramProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TelegramLongPollingService(TelegramApiClient telegramApiClient,
                                      TelegramBotService botService,
                                      TelegramProperties properties) {
        this.telegramApiClient = telegramApiClient;
        this.botService = botService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || properties.transport() != TelegramProperties.Transport.LONG_POLLING) {
            return;
        }
        executor.submit(this::poll);
    }

    private void poll() {
        long offset = 0;
        telegramApiClient.deleteWebhook();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<TelegramUpdate> updates = telegramApiClient.getUpdates(offset, 30);
                for (TelegramUpdate update : updates) {
                    botService.handle(update);
                    offset = update.updateId() + 1;
                }
            } catch (RuntimeException exception) {
                log.warn("Telegram long polling failed", exception);
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
