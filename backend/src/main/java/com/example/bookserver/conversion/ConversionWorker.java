package com.example.bookserver.conversion;

import com.example.bookserver.conversion.exception.ConverterNotFoundException;
import com.example.bookserver.domain.ConversionJob;
import com.example.bookserver.domain.ConversionStatus;
import com.example.bookserver.repo.ConversionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Async executor for conversion jobs. Resolves a {@link FormatConverter} for the
 * job's {@code (sourceFormat -> targetFormat)} pair and invokes it. Any failure
 * (including the expected {@link NotImplementedFormatConverter}) is recorded on
 * the job as {@link ConversionStatus#FAILED} with the exception message.
 */
@Component
public class ConversionWorker {

    private static final Logger log = LoggerFactory.getLogger(ConversionWorker.class);

    private final ConversionJobRepository conversionJobRepository;
    private final FormatConverterRegistry registry;
    private final TransactionTemplate transactionTemplate;

    public ConversionWorker(ConversionJobRepository conversionJobRepository,
                            FormatConverterRegistry registry,
                            TransactionTemplate transactionTemplate) {
        this.conversionJobRepository = conversionJobRepository;
        this.registry = registry;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    public void run(Long jobId) {
        try {
            JobContext ctx = markRunning(jobId);
            FormatConverter converter = registry.resolve(ctx.sourceFormat(), ctx.targetFormat())
                    .orElseThrow(() -> new ConverterNotFoundException(ctx.sourceFormat(), ctx.targetFormat()));
            // A real converter receives the source file path; the storage-relative
            // path is enough for the current architecture slot.
            Path source = Path.of(ctx.storagePath());
            converter.convert(source, ctx.targetFormat());
            markFinished(jobId, ConversionStatus.SUCCEEDED, null);
        } catch (Exception e) {
            log.warn("Conversion job {} failed: {}", jobId, e.getMessage());
            markFinished(jobId, ConversionStatus.FAILED, e.getMessage());
        }
    }

    private JobContext markRunning(Long jobId) {
        return transactionTemplate.execute(status -> {
            ConversionJob job = conversionJobRepository.findById(jobId).orElseThrow();
            job.setStatus(ConversionStatus.RUNNING);
            job.setStartedAt(now());
            conversionJobRepository.save(job);
            return new JobContext(
                    job.getBookFile().getFormat(),
                    job.getTargetFormat(),
                    job.getBookFile().getStoragePath());
        });
    }

    private void markFinished(Long jobId, ConversionStatus status, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            ConversionJob job = conversionJobRepository.findById(jobId).orElseThrow();
            job.setStatus(status);
            job.setMessage(message);
            job.setFinishedAt(now());
            conversionJobRepository.save(job);
        });
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record JobContext(String sourceFormat, String targetFormat, String storagePath) {
    }
}
