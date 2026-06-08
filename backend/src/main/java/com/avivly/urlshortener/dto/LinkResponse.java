package com.avivly.urlshortener.dto;

import com.avivly.urlshortener.model.ShortLink;
import java.time.LocalDateTime;

public record LinkResponse(
    Long id,
    String shortCode,
    String originalUrl,
    String strategy,
    boolean isActive,
    Integer maxClicks,
    int totalClicks,
    LocalDateTime expiresAt,
    String tags,
    LocalDateTime createdAt,
    Long ownerId
) {
    public static LinkResponse from(ShortLink link) {
        return new LinkResponse(
            link.getId(), link.getShortCode(), link.getOriginalUrl(),
            link.getStrategy(), link.isActive(), link.getMaxClicks(),
            link.getTotalClicks(), link.getExpiresAt(), link.getTags(),
            link.getCreatedAt(),
            link.getOwner() != null ? link.getOwner().getId() : null
        );
    }
}
