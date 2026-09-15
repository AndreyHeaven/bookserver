package com.example.bookserver.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.net.URI;

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

    @Bean
    public TelegramUrl telegramUrl(TelegramProperties properties) {
        URI uri = URI.create(properties.apiUrl());
        return TelegramUrl.builder()
                .schema(uri.getScheme())
                .host(uri.getHost())
                .port(uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort())
                .build();
    }

    @Bean
    public TelegramClient telegramClient(TelegramProperties properties, TelegramUrl telegramUrl) {
        return new OkHttpTelegramClient(properties.botToken(), telegramUrl);
    }

    private static int defaultPort(String scheme) {
        return "http".equalsIgnoreCase(scheme) ? 80 : 443;
    }
}
