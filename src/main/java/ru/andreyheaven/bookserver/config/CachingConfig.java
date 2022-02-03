package ru.andreyheaven.bookserver.config;

import org.springframework.cache.*;
import org.springframework.cache.concurrent.*;
import org.springframework.cache.support.*;
import org.springframework.context.annotation.*;
import java.util.*;

@Configuration
class CachingConfig {

    /*@Bean
    public CacheManager cacheManager() {
        var cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(
                List.of(
                        new ConcurrentMapCache(
                                "author_index",
//                                CacheBuilder
//                                        .newBuilder()
//                                        .expireAfterWrite(12, TimeUnit.HOURS)
//                                        .maximumSize(100)
//                                        .build()
//                                        .asMap(),
                                false
                        ),
                        new ConcurrentMapCache("authors", false)
                )
        );
        return cacheManager;
    }*/
}
