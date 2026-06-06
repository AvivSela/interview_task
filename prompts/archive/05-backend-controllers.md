# Agent Prompt: REST Controllers — RedirectController & LinkController (TASK-16, TASK-17)

## Project Context
You are building an **analytics-driven URL shortener**.

The following services already exist:

**`LinkService`** (in `com.avivly.urlshortener.service`):
- `ShortLink findByShortCode(String shortCode)` — returns null if not found
- `List<ShortLink> findAll()`
- `ShortLink create(CreateLinkRequest req)` — throws `ResponseStatusException(CONFLICT)` if taken
- `ShortLink update(Long id, UpdateLinkRequest req)`
- `void delete(Long id)`

**`AnalyticsService`** (in `com.avivly.urlshortener.service`):
- `void logClickAsync(String shortCode, String referer, String userAgent, String ip)`
- `AnalyticsResponse getAnalytics(String shortCode)`

**`ShortLink`** model has `isValid()` which returns false if expired, inactive, or click-exhausted.

**DTOs:** `CreateLinkRequest`, `UpdateLinkRequest`, `AnalyticsResponse` in `com.avivly.urlshortener.dto`.

## Your Task
Create the two REST controller classes.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/controller/RedirectController.java`
```java
package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        ShortLink link = linkService.findByShortCode(shortCode);
        if (link == null || !link.isValid()) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        analyticsService.logClickAsync(
            shortCode,
            request.getHeader("Referer"),
            request.getHeader("User-Agent"),
            request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(link.getOriginalUrl()))
            .build();
    }
}
```

### `backend/src/main/java/com/avivly/urlshortener/controller/LinkController.java`
```java
package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.dto.AnalyticsResponse;
import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    @PostMapping
    public ResponseEntity<ShortLink> create(@Valid @RequestBody CreateLinkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(req));
    }

    @GetMapping
    public List<ShortLink> getAll() {
        return linkService.findAll();
    }

    @PutMapping("/{id}")
    public ShortLink update(@PathVariable Long id, @Valid @RequestBody UpdateLinkRequest req) {
        return linkService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        linkService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        return analyticsService.getAnalytics(shortCode);
    }
}
```

## Acceptance Criteria
- Both files compile without errors
- `RedirectController.redirect`: returns `302 Found` with `Location` header on valid link; returns `410 Gone` when link is null or `isValid()` returns false; calls `logClickAsync` (non-blocking, never waits on it)
- `LinkController` exposes all 5 endpoints at `/api/links`
- `POST /api/links` returns `201 Created`
- `DELETE /api/links/{id}` returns `204 No Content`
- `@Valid` is on request body parameters for POST and PUT
