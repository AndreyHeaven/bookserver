package com.example.bookserver.imports.dto;

import com.example.bookserver.domain.ImportJob;
import com.example.bookserver.domain.ImportStatus;
import com.example.bookserver.imports.ImportJobMessage;
import com.example.bookserver.imports.ImportJobMessageLevel;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

public record ImportJobDto(Long id,
                           String importerType,
                           String sourcePath,
                           ImportStatus status,
                           String message,
                           List<ImportJobMessage> messages,
                           OffsetDateTime createdAt,
                           OffsetDateTime startedAt,
                           OffsetDateTime finishedAt,
                           long totalCount,
                           long processedCount) {

    public static ImportJobDto from(ImportJob job) {
        return new ImportJobDto(
                job.getId(),
                job.getImporterType(),
                job.getSourcePath(),
                job.getStatus(),
                job.getMessage(),
                messagesFrom(job.getMessage()),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTotalCount(),
                job.getProcessedCount());
    }

    private static List<ImportJobMessage> messagesFrom(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(ImportJobDto::messageFrom)
                .toList();
    }

    private static ImportJobMessage messageFrom(String line) {
        if (line.startsWith("[WARNING] ")) {
            return new ImportJobMessage(ImportJobMessageLevel.WARNING, line.substring(10));
        }
        if (line.startsWith("[ERROR] ")) {
            return new ImportJobMessage(ImportJobMessageLevel.ERROR, line.substring(8));
        }
        return new ImportJobMessage(ImportJobMessageLevel.ERROR, line);
    }
}
