package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Map<String, Object> strategyParams,
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
