package com.example.bookserver.conversion;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link FormatConverter} by {@link FormatPair}. All converter beans
 * are collected at startup; the first bean claiming a pair wins.
 */
@Component
public class FormatConverterRegistry {

    private final Map<FormatPair, FormatConverter> byPair = new HashMap<>();

    public FormatConverterRegistry(List<FormatConverter> converters) {
        for (FormatConverter converter : converters) {
            for (FormatPair pair : converter.supportedPairs()) {
                byPair.putIfAbsent(pair, converter);
            }
        }
    }

    public Optional<FormatConverter> resolve(String sourceFormat, String targetFormat) {
        return Optional.ofNullable(byPair.get(new FormatPair(sourceFormat, targetFormat)));
    }
}
