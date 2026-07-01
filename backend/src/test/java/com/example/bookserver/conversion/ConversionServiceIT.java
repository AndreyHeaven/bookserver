package com.example.bookserver.conversion;

import com.example.bookserver.AbstractIntegrationTest;
import com.example.bookserver.conversion.dto.StartConversionRequest;
import com.example.bookserver.conversion.exception.ConverterNotFoundException;
import com.example.bookserver.domain.ConversionStatus;
import com.example.bookserver.repo.ConversionJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionServiceIT extends AbstractIntegrationTest {

    @Autowired ConversionService conversionService;
    @Autowired ConversionJobRepository conversionJobRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void conversion_to_supported_pair_fails_with_not_implemented_message() throws Exception {
        Long bookFileId = insertBookFile("fb2");

        Long jobId = conversionService.startConversion(
                new StartConversionRequest(bookFileId, "epub")).id();
        awaitStatus(jobId, ConversionStatus.FAILED);

        assertThat(conversionJobRepository.findById(jobId).orElseThrow().getMessage())
                .isEqualTo(NotImplementedFormatConverter.MESSAGE);
    }

    @Test
    void conversion_to_unsupported_pair_is_rejected() {
        Long bookFileId = insertBookFile("fb2");

        // "txt" is not among SupportedFormat, so no converter is registered.
        assertThatThrownBy(() -> conversionService.startConversion(
                new StartConversionRequest(bookFileId, "txt")))
                .isInstanceOf(ConverterNotFoundException.class);
    }

    private void awaitStatus(Long jobId, ConversionStatus status) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (conversionJobRepository.findById(jobId).orElseThrow().getStatus() == status) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(conversionJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(status);
    }

    private Long insertBookFile(String format) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long bookId = jdbc.queryForObject(
                "INSERT INTO books(title, lang, year, file_type) VALUES (?, 'ru', 2020, ?) RETURNING id",
                Long.class, "Conversion Book " + suffix, format);
        return jdbc.queryForObject(
                "INSERT INTO book_files(book_id, format, storage_path, size_bytes) VALUES (?, ?, ?, 100) RETURNING id",
                Long.class, bookId, format, "/tmp/conv-" + suffix + "." + format);
    }
}
