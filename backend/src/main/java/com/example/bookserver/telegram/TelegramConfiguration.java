package com.example.bookserver.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramConfiguration {

    @Bean
    public Cache<String, TelegramAction> telegramActionCache(TelegramProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.actionTtl())
                .maximumSize(10_000)
                .build();
    }

    @Bean
    public Cache<String, TelegramAction.Download> telegramDownloadCache(TelegramProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.downloadTtl())
                .maximumSize(10_000)
                .build();
    }
}
