package com.example.bookserver.conversion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartConversionRequest(@NotNull Long bookFileId,
                                     @NotBlank String targetFormat) {
}
