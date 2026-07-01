package com.example.bookserver.conversion;

import com.example.bookserver.conversion.dto.ConversionJobDto;
import com.example.bookserver.conversion.dto.StartConversionRequest;
import com.example.bookserver.conversion.exception.ConverterNotFoundException;
import com.example.bookserver.domain.BookFile;
import com.example.bookserver.domain.ConversionJob;
import com.example.bookserver.repo.BookFileRepository;
import com.example.bookserver.repo.ConversionJobRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversionService {

    private final ConversionJobRepository conversionJobRepository;
    private final BookFileRepository bookFileRepository;
    private final FormatConverterRegistry registry;
    private final ConversionWorker worker;

    public ConversionService(ConversionJobRepository conversionJobRepository,
                             BookFileRepository bookFileRepository,
                             FormatConverterRegistry registry,
                             ConversionWorker worker) {
        this.conversionJobRepository = conversionJobRepository;
        this.bookFileRepository = bookFileRepository;
        this.registry = registry;
        this.worker = worker;
    }

    @Transactional
    public ConversionJobDto startConversion(StartConversionRequest request) {
        BookFile source = bookFileRepository.findById(request.bookFileId())
                .orElseThrow(() -> new EntityNotFoundException("Book file not found: " + request.bookFileId()));
        String sourceFormat = source.getFormat();
        String targetFormat = request.targetFormat();
        // Fail fast (HTTP 422) when no converter can handle the requested pair,
        // so we don't persist a job that can never make progress.
        if (registry.resolve(sourceFormat, targetFormat).isEmpty()) {
            throw new ConverterNotFoundException(sourceFormat, targetFormat);
        }

        ConversionJob job = new ConversionJob();
        job.setBookFile(source);
        job.setTargetFormat(targetFormat);
        job = conversionJobRepository.save(job);
        worker.run(job.getId());
        return ConversionJobDto.from(job);
    }

    @Transactional(readOnly = true)
    public Page<ConversionJobDto> list(Pageable pageable) {
        return conversionJobRepository.findAll(pageable).map(ConversionJobDto::from);
    }

    @Transactional(readOnly = true)
    public ConversionJobDto get(Long id) {
        return ConversionJobDto.from(conversionJobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Conversion job not found: " + id)));
    }
}
