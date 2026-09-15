package com.example.bookserver.telegram;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        boolean enabled,
        String botToken,
        String publicBaseUrl,
        /** Bot API endpoint; overridable so tests can point the client at a stand-in server. */
        String apiUrl,
        @Min(1) @Max(20) int pageSize,
        Duration actionTtl,
        Duration downloadTtl) {

    private static final String DEFAULT_API_URL = "https://api.telegram.org/";

    public TelegramProperties {
        if (enabled && (isBlank(botToken) || isBlank(publicBaseUrl))) {
            throw new IllegalArgumentException("Telegram bot token and public URL are required when the bot is enabled");
        }
        apiUrl = isBlank(apiUrl) ? DEFAULT_API_URL : apiUrl;
        if (actionTtl == null || actionTtl.isNegative() || actionTtl.isZero()) {
            throw new IllegalArgumentException("app.telegram.action-ttl must be positive");
        }
        if (downloadTtl == null || downloadTtl.isNegative() || downloadTtl.isZero()) {
            throw new IllegalArgumentException("app.telegram.download-ttl must be positive");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
