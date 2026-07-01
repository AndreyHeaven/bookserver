package com.example.bookserver.lists.dto;

public record ShareLinkDto(String token,
                           String publicUrl,
                           String qrPngBase64) {
}
