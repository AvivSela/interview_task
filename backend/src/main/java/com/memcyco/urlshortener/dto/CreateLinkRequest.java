package com.memcyco.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    @Schema(description = "Code-generation strategy: RANDOM_BASE62 (default), HASH_TRUNCATE, or SEQUENTIAL")
    String strategy,
    @Schema(description = "Strategy-specific parameters (e.g. {\"length\": 8} for RANDOM_BASE62)")
    Map<String, Object> strategyParams,
    Integer maxClicks,
    @Schema(description = "ISO-8601 datetime when the link expires. Null = never expires.")
    LocalDateTime expiresAt,
    String tags
) {}
