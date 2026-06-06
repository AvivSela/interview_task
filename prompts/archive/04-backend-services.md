# Agent Prompt: Service Layer — LinkService & AnalyticsService (TASK-14, TASK-15)

## Project Context
You are building an **analytics-driven URL shortener**.

The following classes already exist and compile:

**Repositories (in `com.avivly.urlshortener.repository`):**
- `ShortLinkRepository` — extends `JpaRepository<ShortLink, Long>`
  - `Optional<ShortLink> findByShortCode(String shortCode)`
  - `void incrementClicks(String shortCode)` — `@Modifying @Query`
- `ClickAnalyticsRepository` — extends `JpaRepository<ClickAnalytics, Long>`
  - `List<ClickAnalytics> findByShortCodeOrderByClickedAtDesc(String shortCode)`
  - `List<Object[]> countClicksByDay(String shortCode)`
  - `List<Object[]> topReferrers(String shortCode)`
  - `List<Object[]> topUserAgents(String shortCode)`

**DTOs (in `com.avivly.urlshortener.dto`):**
- `CreateLinkRequest(originalUrl, customAlias, strategy, maxClicks, expiresAt, tags)`
- `UpdateLinkRequest(originalUrl, isActive, expiresAt, tags, maxClicks)`
- `AnalyticsResponse(totalClicks, clicksOverTime, topReferrers, topUserAgents)`
  - Inner records: `DailyCount(date, count)`, `ReferrerCount(referer, count)`, `AgentCount(userAgent, count)`

**Config:** Cache named `"shortLinks"` via Caffeine; async executor with prefix `"analytics-"`.

**Utility:** `Base62.generate(int length)` in `com.avivly.urlshortener.util`.

## Your Task
Create both service classes.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java`
```java
package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.repository.ShortLinkRepository;
import com.avivly.urlshortener.util.Base62;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final ShortLinkRepository repo;

    @Cacheable(value = "shortLinks", key = "#shortCode")
    public ShortLink findByShortCode(String shortCode) {
        return repo.findByShortCode(shortCode).orElse(null);
    }

    public List<ShortLink> findAll() {
        return repo.findAll();
    }

    @Transactional
    public ShortLink create(CreateLinkRequest req) {
        String code = (req.customAlias() != null && !req.customAlias().isBlank())
            ? req.customAlias()
            : Base62.generate(7);

        if (repo.findByShortCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
        }

        ShortLink link = ShortLink.builder()
            .shortCode(code)
            .originalUrl(req.originalUrl())
            .strategy(req.strategy() != null ? req.strategy() : "RANDOM_BASE62")
            .maxClicks(req.maxClicks())
            .expiresAt(req.expiresAt())
            .tags(req.tags())
            .build();

        return repo.save(link);
    }

    @Transactional
    @CacheEvict(value = "shortLinks", key = "#result.shortCode")
    public ShortLink update(Long id, UpdateLinkRequest req) {
        ShortLink link = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found: " + id));

        if (req.originalUrl() != null) link.setOriginalUrl(req.originalUrl());
        if (req.isActive() != null) link.setActive(req.isActive());
        if (req.expiresAt() != null) link.setExpiresAt(req.expiresAt());
        if (req.tags() != null) link.setTags(req.tags());
        if (req.maxClicks() != null) link.setMaxClicks(req.maxClicks());

        return repo.save(link);
    }

    @Transactional
    public void delete(Long id) {
        ShortLink link = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found: " + id));
        evictCache(link.getShortCode());
        repo.delete(link);
    }

    @CacheEvict(value = "shortLinks", key = "#shortCode")
    public void evictCache(String shortCode) {}
}
```

### `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`
```java
package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.AnalyticsResponse;
import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.repository.ClickAnalyticsRepository;
import com.avivly.urlshortener.repository.ShortLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickAnalyticsRepository clickRepo;
    private final ShortLinkRepository linkRepo;

    @Async
    @Transactional
    public void logClickAsync(String shortCode, String referer, String userAgent, String ip) {
        clickRepo.save(ClickAnalytics.builder()
            .shortCode(shortCode)
            .referer(referer)
            .userAgent(userAgent)
            .ipAddress(ip)
            .build());
        linkRepo.incrementClicks(shortCode);
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        int totalClicks = linkRepo.findByShortCode(shortCode)
            .map(l -> l.getTotalClicks())
            .orElse(0);

        List<AnalyticsResponse.DailyCount> clicksOverTime = clickRepo.countClicksByDay(shortCode)
            .stream()
            .map(row -> new AnalyticsResponse.DailyCount(
                row[0] != null ? row[0].toString() : "",
                row[1] != null ? ((Number) row[1]).longValue() : 0L
            ))
            .toList();

        List<AnalyticsResponse.ReferrerCount> topReferrers = clickRepo.topReferrers(shortCode)
            .stream()
            .map(row -> new AnalyticsResponse.ReferrerCount(
                row[0] != null ? row[0].toString() : "",
                row[1] != null ? ((Number) row[1]).longValue() : 0L
            ))
            .toList();

        List<AnalyticsResponse.AgentCount> topUserAgents = clickRepo.topUserAgents(shortCode)
            .stream()
            .map(row -> new AnalyticsResponse.AgentCount(
                row[0] != null ? row[0].toString() : "",
                row[1] != null ? ((Number) row[1]).longValue() : 0L
            ))
            .toList();

        return new AnalyticsResponse(totalClicks, clicksOverTime, topReferrers, topUserAgents);
    }
}
```

## Acceptance Criteria
- Both services compile without errors
- `LinkService.findByShortCode` has `@Cacheable(value="shortLinks", key="#shortCode")`
- `LinkService.update` has `@CacheEvict` — cache key must be the short code (not the id)
- `LinkService.delete` evicts the cache before deleting
- `LinkService.create` throws `ResponseStatusException(CONFLICT)` if the short code is already taken
- `AnalyticsService.logClickAsync` has both `@Async` and `@Transactional`
- `AnalyticsService.getAnalytics` maps all three `Object[]` query results to the correct nested record types
