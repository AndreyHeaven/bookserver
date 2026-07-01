package com.example.bookserver.conversion.dto;

import com.example.bookserver.domain.ConversionJob;
import com.example.bookserver.domain.ConversionStatus;

import java.time.OffsetDateTime;

public record ConversionJobDto(Long id,
                               Long bookFileId,
                               String sourceFormat,
                               String targetFormat,
                               ConversionStatus status,
                               String message,
                               Long outputBookFileId,
                               OffsetDateTime createdAt,
                               OffsetDateTime startedAt,
                               OffsetDateTime finishedAt) {

    public static ConversionJobDto from(ConversionJob job) {
        return new ConversionJobDto(
                job.getId(),
                job.getBookFile().getId(),
                job.getBookFile().getFormat(),
                job.getTargetFormat(),
                job.getStatus(),
                job.getMessage(),
                job.getOutputBookFile() == null ? null : job.getOutputBookFile().getId(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
