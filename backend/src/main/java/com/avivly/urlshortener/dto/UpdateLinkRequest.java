package com.avivly.urlshortener.dto;

import java.time.LocalDateTime;

public record UpdateLinkRequest(
    String originalUrl,
    Boolean isActive,
    LocalDateTime expiresAt,
    String tags,
    Integer maxClicks
) {}
