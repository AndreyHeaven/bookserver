package com.example.bookserver.imports;

import com.example.bookserver.domain.ImportJob;
import com.example.bookserver.imports.dto.ImportJobDto;
import com.example.bookserver.imports.dto.StartImportRequest;
import com.example.bookserver.repo.ImportJobRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Map;

@Service
public class ImportService {

    private final ImportJobRepository importJobRepository;
    private final ImportWorker worker;
    private final Path baseDir;

    public ImportService(ImportJobRepository importJobRepository,
                         ImportWorker worker,
                         @Value("${app.imports.base-dir:./data/imports}") String baseDir) {
        this.importJobRepository = importJobRepository;
        this.worker = worker;
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Transactional
    public ImportJobDto startImport(StartImportRequest request) {
        Path source = resolveSource(request.sourcePath());
        ImportJob job = new ImportJob();
        job.setImporterType(request.type());
        job.setSourcePath(source.toString());
        job = importJobRepository.save(job);
        worker.run(job.getId(), request.type(), source, request.options() == null ? Map.of() : request.options());
        return ImportJobDto.from(job);
    }

    @Transactional(readOnly = true)
    public Page<ImportJobDto> list(Pageable pageable) {
        return importJobRepository.findAll(pageable).map(ImportJobDto::from);
    }

    @Transactional(readOnly = true)
    public ImportJobDto get(Long id) {
        return ImportJobDto.from(importJobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Import job not found: " + id)));
    }

    private Path resolveSource(String sourcePath) {
        Path raw = Path.of(sourcePath);
        Path resolved = raw.isAbsolute() ? raw.normalize() : baseDir.resolve(raw).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Import source path must be inside " + baseDir);
        }
        return resolved;
    }
}
