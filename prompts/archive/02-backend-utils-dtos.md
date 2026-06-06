# Agent Prompt: Base62 Utility & DTOs (TASK-12, TASK-13)

## Project Context
You are building an **analytics-driven URL shortener**.
The backend Maven project already exists at `backend/pom.xml` with group `com.avivly`, artifact `urlshortener`, Java 17, Spring Boot 3.x.
Dependencies include `spring-boot-starter-validation`.

## Your Task
Create the `Base62` utility class and all request/response DTOs.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/util/Base62.java`
```java
package com.avivly.urlshortener.util;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Base62 {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        return IntStream.range(0, length)
            .mapToObj(i -> String.valueOf(CHARS.charAt(RANDOM.nextInt(62))))
            .collect(Collectors.joining());
    }
}
```

### `backend/src/main/java/com/avivly/urlshortener/dto/CreateLinkRequest.java`
```java
package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
```

### `backend/src/main/java/com/avivly/urlshortener/dto/UpdateLinkRequest.java`
```java
package com.avivly.urlshortener.dto;

import java.time.LocalDateTime;

public record UpdateLinkRequest(
    String originalUrl,
    Boolean isActive,
    LocalDateTime expiresAt,
    String tags,
    Integer maxClicks
) {}
```

### `backend/src/main/java/com/avivly/urlshortener/dto/AnalyticsResponse.java`
```java
package com.avivly.urlshortener.dto;

import java.util.List;

public record AnalyticsResponse(
    int totalClicks,
    List<DailyCount> clicksOverTime,
    List<ReferrerCount> topReferrers,
    List<AgentCount> topUserAgents
) {
    public record DailyCount(String date, long count) {}
    public record ReferrerCount(String referer, long count) {}
    public record AgentCount(String userAgent, long count) {}
}
```

## Acceptance Criteria
- All 4 files compile without errors
- `Base62.generate(7)` produces a 7-character alphanumeric string using `SecureRandom`
- `@NotBlank` on `CreateLinkRequest.originalUrl` is imported from `jakarta.validation.constraints`
- All three DTO records exist with the exact field names and types shown
- Nested records inside `AnalyticsResponse` compile correctly
