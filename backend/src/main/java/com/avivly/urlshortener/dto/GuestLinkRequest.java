package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;

public record GuestLinkRequest(
    @NotBlank String originalUrl,
    String customAlias
) {}
