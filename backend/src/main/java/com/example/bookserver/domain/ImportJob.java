package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Maps to {@code import_jobs} (Liquibase 005-001). */
@Entity
@Table(name = "import_jobs")
@EntityListeners(AuditingEntityListener.class)
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "importer_type", nullable = false, length = 64)
    private String importerType;

    @Column(name = "source_path", nullable = false, length = 1024)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "total_count", nullable = false)
    private long totalCount = 0L;

    @Column(name = "processed_count", nullable = false)
    private long processedCount = 0L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getImporterType() { return importerType; }
    public void setImporterType(String importerType) { this.importerType = importerType; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public ImportStatus getStatus() { return status; }
    public void setStatus(ImportStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getProcessedCount() { return processedCount; }
    public void setProcessedCount(long processedCount) { this.processedCount = processedCount; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImportJob that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
