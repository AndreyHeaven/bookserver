package com.example.bookserver.imports;

import com.example.bookserver.domain.ImportJob;
import com.example.bookserver.domain.ImportStatus;
import com.example.bookserver.repo.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class ImportWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

    private final ImportJobRepository importJobRepository;
    private final ImporterRegistry registry;
    private final TransactionTemplate transactionTemplate;

    public ImportWorker(ImportJobRepository importJobRepository,
                        ImporterRegistry registry,
                        TransactionTemplate transactionTemplate) {
        this.importJobRepository = importJobRepository;
        this.registry = registry;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    public void run(Long jobId, String type, Path source, Map<String, String> options) {
        try {
            markRunning(jobId);
            BookImporter importer = registry.get(type);
            ImportContext context = new ImportContext(jobId, type, source, options);
            importer.importFrom(context, (processed, total) -> updateProgress(jobId, processed, total));
            markFinished(jobId, ImportStatus.SUCCEEDED, null);
        } catch (Exception e) {
            log.error("Import job {} (type={}, source={}) failed", jobId, type, source, e);
            markFinished(jobId, ImportStatus.FAILED, e.getMessage());
        }
    }

    private void markRunning(Long jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            job.setStatus(ImportStatus.RUNNING);
            job.setStartedAt(now());
            importJobRepository.save(job);
        });
    }

    private void updateProgress(Long jobId, long processed, long total) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            job.setProcessedCount(processed);
            job.setTotalCount(total);
            importJobRepository.save(job);
        });
    }

    private void markFinished(Long jobId, ImportStatus status, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            job.setStatus(status);
            job.setMessage(message);
            job.setFinishedAt(now());
            importJobRepository.save(job);
        });
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
