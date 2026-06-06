# Agent Prompt: JPA Repositories (TASK-07, TASK-08)

## Project Context
You are building an **analytics-driven URL shortener**.
The following entity classes already exist:

**`backend/src/main/java/com/avivly/urlshortener/model/ShortLink.java`**
- Fields: `id` (Long), `shortCode` (String), `originalUrl` (String), `strategy`, `isActive`, `maxClicks`, `totalClicks`, `expiresAt`, `tags`, `createdAt`

**`backend/src/main/java/com/avivly/urlshortener/model/ClickAnalytics.java`**
- Fields: `id` (Long), `shortCode` (String), `clickedAt` (LocalDateTime), `referer`, `userAgent`, `ipAddress`

## Your Task
Create the two Spring Data JPA repository interfaces.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/repository/ShortLinkRepository.java`
```java
package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortLink s SET s.totalClicks = s.totalClicks + 1 WHERE s.shortCode = :shortCode")
    void incrementClicks(@Param("shortCode") String shortCode);
}
```

### `backend/src/main/java/com/avivly/urlshortener/repository/ClickAnalyticsRepository.java`
```java
package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickAnalyticsRepository extends JpaRepository<ClickAnalytics, Long> {

    List<ClickAnalytics> findByShortCodeOrderByClickedAtDesc(String shortCode);

    @Query("SELECT DATE(c.clickedAt) as date, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode GROUP BY DATE(c.clickedAt) ORDER BY DATE(c.clickedAt)")
    List<Object[]> countClicksByDay(@Param("shortCode") String shortCode);

    @Query("SELECT c.referer, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode AND c.referer IS NOT NULL GROUP BY c.referer ORDER BY count DESC")
    List<Object[]> topReferrers(@Param("shortCode") String shortCode);

    @Query("SELECT c.userAgent, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode AND c.userAgent IS NOT NULL GROUP BY c.userAgent ORDER BY count DESC")
    List<Object[]> topUserAgents(@Param("shortCode") String shortCode);
}
```

## Acceptance Criteria
- Both interfaces compile without errors
- `ShortLinkRepository.incrementClicks` has `@Modifying` and `@Query` annotations — no `@Transactional` here (the caller handles it)
- `ClickAnalyticsRepository` has exactly 4 query methods: `findByShortCodeOrderByClickedAtDesc`, `countClicksByDay`, `topReferrers`, `topUserAgents`
- All JPQL queries use the entity class name (`ClickAnalytics`, `ShortLink`), not the table name
