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
        Transport transport,
        String botToken,
        String webhookSecret,
        String publicBaseUrl,
        @Min(1) @Max(20) int pageSize,
        Duration actionTtl,
        Duration downloadTtl) {

    public TelegramProperties {
        if (enabled && (transport == null || isBlank(botToken) || isBlank(publicBaseUrl)
                || (transport == Transport.WEBHOOK && isBlank(webhookSecret)))) {
            throw new IllegalArgumentException("Telegram transport, bot token and public URL are required; webhook also requires its secret");
        }
        if (actionTtl == null || actionTtl.isNegative() || actionTtl.isZero()) {
            throw new IllegalArgumentException("app.telegram.action-ttl must be positive");
        }
        if (downloadTtl == null || downloadTtl.isNegative() || downloadTtl.isZero()) {
            throw new IllegalArgumentException("app.telegram.download-ttl must be positive");
        }
    }

    public enum Transport {
        WEBHOOK,
        LONG_POLLING
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
