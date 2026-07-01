package com.example.bookserver.conversion;

import com.example.bookserver.conversion.dto.ConversionJobDto;
import com.example.bookserver.conversion.dto.StartConversionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversions")
@Tag(name = "Conversions")
public class ConversionsController {

    private final ConversionService service;

    public ConversionsController(ConversionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start an asynchronous format conversion")
    public ConversionJobDto start(@Valid @RequestBody StartConversionRequest request) {
        return service.startConversion(request);
    }

    @GetMapping
    @Operation(summary = "List conversion jobs")
    public Page<ConversionJobDto> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversion job details")
    public ConversionJobDto get(@PathVariable Long id) {
        return service.get(id);
    }
}
