package com.example.bookserver.imports.dto;

import com.example.bookserver.domain.ImportJob;
import com.example.bookserver.domain.ImportStatus;

import java.time.OffsetDateTime;

public record ImportJobDto(Long id,
                           String importerType,
                           String sourcePath,
                           ImportStatus status,
                           String message,
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
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTotalCount(),
                job.getProcessedCount());
    }
}
