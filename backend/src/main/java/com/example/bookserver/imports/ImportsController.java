package com.example.bookserver.imports;

import com.example.bookserver.imports.dto.ImportJobDto;
import com.example.bookserver.imports.dto.StartImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imports")
@Tag(name = "Imports")
public class ImportsController {

    private final ImportService service;

    public ImportsController(ImportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start an asynchronous import")
    public ImportJobDto start(@Valid @RequestBody StartImportRequest request) {
        return service.startImport(request);
    }

    @GetMapping
    @Operation(summary = "List import jobs")
    public Page<ImportJobDto> list(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import job details")
    public ImportJobDto get(@PathVariable Long id) {
        return service.get(id);
    }
}
