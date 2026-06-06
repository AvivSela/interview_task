# Agent Prompt: JPA Entities — ShortLink & ClickAnalytics (TASK-05, TASK-06)

## Project Context
You are building an **analytics-driven URL shortener**.
The backend Maven project already exists at `backend/pom.xml` with:
- Group `com.avivly`, artifact `urlshortener`, Java 17, Spring Boot 3.x
- Dependencies include `spring-boot-starter-data-jpa`, `lombok`, `postgresql`

## Your Task
Create the two JPA entity classes.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/model/ShortLink.java`
```java
package com.avivly.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String shortCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Builder.Default
    private String strategy = "RANDOM_BASE62";

    @Builder.Default
    private boolean isActive = true;

    private Integer maxClicks;

    @Builder.Default
    private int totalClicks = 0;

    private LocalDateTime expiresAt;

    private String tags;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isValid() {
        if (!isActive) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (maxClicks != null && totalClicks >= maxClicks) return false;
        return true;
    }
}
```

### `backend/src/main/java/com/avivly/urlshortener/model/ClickAnalytics.java`
```java
package com.avivly.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_analytics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortCode;

    private LocalDateTime clickedAt;

    @Column(columnDefinition = "TEXT")
    private String referer;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(length = 45)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }
}
```

## Acceptance Criteria
- Both files compile without errors
- `ShortLink` maps to table `short_links`; `ClickAnalytics` maps to `click_analytics`
- All Lombok annotations present: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- `ShortLink.isValid()` method checks all three conditions: active flag, expiry, max clicks
- `@PrePersist` sets timestamp fields if null
- `@Column(updatable = false)` on `ShortLink.createdAt`
- `@Builder.Default` used for fields with default values in `ShortLink`
