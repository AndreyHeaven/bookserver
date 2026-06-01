package com.example.bookserver.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
@EnableJpaRepositories(basePackages = "com.example.bookserver.repo")
@EntityScan(basePackages = "com.example.bookserver.domain")
public class JpaConfig {

    /**
     * Provides {@link OffsetDateTime} (UTC) for JPA auditing so that
     * {@code @CreatedDate}/{@code @LastModifiedDate} fields declared as
     * {@link OffsetDateTime} are populated correctly. Without this provider
     * Spring Data ships {@code LocalDateTime}, which JPA cannot convert to
     * {@code OffsetDateTime} columns.
     */
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
