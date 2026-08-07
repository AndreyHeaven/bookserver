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
import java.util.ArrayList;
import java.util.List;
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
            importer.importFrom(context, new ImportJobProgress() {
                @Override
                public void update(long processed, long total) {
                    updateProgress(jobId, processed, total);
                }

                @Override
                public void warning(String message) {
                    appendMessage(jobId, ImportJobMessageLevel.WARNING, message);
                }

                @Override
                public void error(String message) {
                    appendMessage(jobId, ImportJobMessageLevel.ERROR, message);
                }
            });
            markFinished(jobId, ImportStatus.SUCCEEDED, null);
        } catch (Exception e) {
            log.error("Import job {} (type={}, source={}) failed", jobId, type, source, e);
            appendMessage(jobId, ImportJobMessageLevel.ERROR,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            markFinished(jobId, ImportStatus.FAILED, null);
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

    private void appendMessage(Long jobId, ImportJobMessageLevel level, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            job.setMessage(serializeMessages(appendMessage(parseMessages(job.getMessage()), level, message)));
            importJobRepository.save(job);
        });
    }

    private static List<ImportJobMessage> parseMessages(String value) {
        List<ImportJobMessage> messages = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return messages;
        }
        for (String line : value.split("\\R")) {
            if (line.startsWith("[WARNING] ")) {
                messages.add(new ImportJobMessage(ImportJobMessageLevel.WARNING, line.substring(10)));
            } else if (line.startsWith("[ERROR] ")) {
                messages.add(new ImportJobMessage(ImportJobMessageLevel.ERROR, line.substring(8)));
            } else if (!line.isBlank()) {
                messages.add(new ImportJobMessage(ImportJobMessageLevel.ERROR, line));
            }
        }
        return messages;
    }

    private static List<ImportJobMessage> appendMessage(List<ImportJobMessage> messages,
                                                          ImportJobMessageLevel level,
                                                          String message) {
        messages.add(new ImportJobMessage(level, message));
        return messages;
    }

    private static String serializeMessages(List<ImportJobMessage> messages) {
        return messages.stream()
                .map(message -> "[" + message.level() + "] " + message.message())
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private void markFinished(Long jobId, ImportStatus status, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            job.setStatus(status);
            if (message != null) {
                job.setMessage(message);
            }
            job.setFinishedAt(now());
            importJobRepository.save(job);
        });
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
