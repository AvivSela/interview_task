# Agent Prompt: Create LinkResponse DTO and Add Owner to ShortLink (AUTH-09)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + JPA).
Working directory: project root. AUTH-01 through AUTH-08 are done.
Relevant existing files:
- `backend/src/main/java/com/avivly/urlshortener/model/ShortLink.java` — entity with fields: id, shortCode, originalUrl, strategy, isActive, maxClicks, totalClicks, expiresAt, tags, createdAt
- `backend/src/main/java/com/avivly/urlshortener/dto/` — existing DTOs directory

## Your Task
Two changes: create a new DTO and add an `owner` field to the existing entity.

## Changes

### 1. Create `backend/src/main/java/com/avivly/urlshortener/dto/LinkResponse.java`

This DTO replaces returning raw `ShortLink` entities from the API (avoids serializing the lazy-loaded `User` graph).

```java
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
```

### 2. Modify `backend/src/main/java/com/avivly/urlshortener/model/ShortLink.java`

Add after the existing `tags` field (before the `createdAt` field):

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User owner;
```

Also add imports if not already present:
```java
import com.avivly.urlshortener.model.User;   // same package — not needed
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
```

## Acceptance Criteria
- `mvn compile -f backend/pom.xml` exits 0
- `ShortLink` has a `getOwner()` method returning `User`
- `LinkResponse.from(link)` correctly maps all fields including `ownerId`
