# Phase 3.1 + 3.3 + 3.4 — Controller IP, Repo Queries, DTO Extension (parallel)

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Tasks 3.1, 3.3, and 3.4 touch different files and are fully independent.
Run them with three parallel subagents.

**Prerequisites:**
- Phase 1.2 complete: `ClickAnalytics` has `geoStatus`, `country`, `city`
- Phase 2.3 complete: `GeoResolverService` exists (referenced by 3.2, not 3.1/3.3/3.4)

---

## Spawn three parallel subagents

---

### Subagent 1 — Task 3.1: Real client IP extraction in `RedirectController` (TDD)

File: `backend/src/main/java/com/avivly/urlshortener/controller/RedirectController.java`

The current implementation passes `request.getRemoteAddr()` directly.
Replace it with a proper extraction method that respects proxy headers.

#### Step 1 — Write the test first

Add a new test class:
`backend/src/test/java/com/avivly/urlshortener/controller/RedirectControllerIpTest.java`

```java
package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerIpTest {

    @Autowired MockMvc mvc;
    @MockBean  LinkService linkService;
    @MockBean  AnalyticsService analyticsService;

    private ShortLink activeLink() {
        return ShortLink.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .isActive(true)
                .totalClicks(0)
                .build();
    }

    @Test
    void xRealIpWinsWhenPresent() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        mvc.perform(get("/abc")
                .header("X-Real-IP", "1.2.3.4")
                .header("X-Forwarded-For", "9.9.9.9"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("1.2.3.4"));
    }

    @Test
    void xffRightToLeftFirstNonPrivate() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        // XFF: client → proxy1(private) → proxy2(public)
        // rightmost non-private is "5.6.7.8"
        mvc.perform(get("/abc")
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 5.6.7.8"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("5.6.7.8"));
    }

    @Test
    void fallsBackToRemoteAddr() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        mvc.perform(get("/abc"))  // no proxy headers; MockMvc uses 127.0.0.1
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("127.0.0.1"));
    }
}
```

Run `cd backend && ./mvnw test -Dtest=RedirectControllerIpTest` — expect red (method
`extractClientIp` does not yet exist).

#### Step 2 — Implement `extractClientIp`

In `RedirectController`, add:

```java
private String extractClientIp(HttpServletRequest request) {
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
        return xRealIp.trim();
    }
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            try {
                InetAddress addr = InetAddress.getByName(candidate);
                if (!addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
    }
    return request.getRemoteAddr();
}
```

Add `import java.net.InetAddress;` at the top.

Replace `request.getRemoteAddr()` in the `redirect` method with `extractClientIp(request)`.

#### Step 3 — Verify

```bash
cd backend && ./mvnw test -Dtest=RedirectControllerIpTest
```

All three tests must be green. Full redirect integration tests must still pass.

---

### Subagent 2 — Task 3.3: Aggregate queries in `ClickAnalyticsRepository`

File: `backend/src/main/java/com/avivly/urlshortener/repository/ClickAnalyticsRepository.java`

Add two native aggregate queries after the existing ones:

```java
@Query(value = """
        SELECT country, COUNT(*) AS clicks
        FROM click_analytics
        WHERE short_code = :shortCode
          AND country IS NOT NULL
        GROUP BY country
        ORDER BY clicks DESC
        LIMIT :limit
        """, nativeQuery = true)
List<Object[]> topCountries(@Param("shortCode") String shortCode,
                             @Param("limit") int limit);

@Query(value = """
        SELECT city, country, COUNT(*) AS clicks
        FROM click_analytics
        WHERE short_code = :shortCode
          AND city IS NOT NULL
        GROUP BY city, country
        ORDER BY clicks DESC
        LIMIT :limit
        """, nativeQuery = true)
List<Object[]> topCities(@Param("shortCode") String shortCode,
                          @Param("limit") int limit);
```

#### Write a `@DataJpaTest` to verify

Create `backend/src/test/java/com/avivly/urlshortener/repository/ClickAnalyticsRepositoryTest.java`:

```java
package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.model.GeoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClickAnalyticsRepositoryTest {

    @Autowired
    private ClickAnalyticsRepository repo;

    @Test
    void topCountriesReturnsOrderedAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("GB").geoStatus(GeoStatus.RESOLVED).build());

        List<Object[]> results = repo.topCountries("x", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)[0]).isEqualTo("US");
        assertThat(((Number) results.get(0)[1]).longValue()).isEqualTo(2);
    }

    @Test
    void topCitiesReturnsOrderedAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").city("New York").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").city("New York").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("GB").city("London").geoStatus(GeoStatus.RESOLVED).build());

        List<Object[]> results = repo.topCities("x", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)[1]).isEqualTo("US");  // country col
        assertThat(results.get(0)[0]).isEqualTo("New York"); // city col
    }

    @Test
    void nullCountryCityExcludedFromAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").geoStatus(GeoStatus.PRIVATE).build());
        assertThat(repo.topCountries("x", 10)).isEmpty();
        assertThat(repo.topCities("x", 10)).isEmpty();
    }
}
```

> Note: H2 (test DB) supports native SQL with GROUP BY. If H2 rejects the query syntax,
> add `MODE=PostgreSQL` to the H2 URL in `application-test.properties`, or switch to
> Testcontainers (per Phase 5.3 guidance).

```bash
cd backend && ./mvnw test -Dtest=ClickAnalyticsRepositoryTest
```

---

### Subagent 3 — Task 3.4: Extend `AnalyticsResponse` DTO

File: `backend/src/main/java/com/avivly/urlshortener/dto/AnalyticsResponse.java`

Current record has: `totalClicks`, `clicksOverTime`, `topReferrers`, `topUserAgents`.

Add two new inner records and two new list fields:

```java
package com.avivly.urlshortener.dto;

import java.util.List;

public record AnalyticsResponse(
    int totalClicks,
    List<DailyCount> clicksOverTime,
    List<ReferrerCount> topReferrers,
    List<AgentCount> topUserAgents,
    List<CountryCount> topCountries,
    List<CityCount> topCities
) {
    public record DailyCount(String date, long count) {}
    public record ReferrerCount(String referer, long count) {}
    public record AgentCount(String userAgent, long count) {}
    public record CountryCount(String country, long clicks) {}
    public record CityCount(String city, String country, long clicks) {}
}
```

**Important:** `AnalyticsService.getAnalytics` will fail to compile because the record
constructor now requires `topCountries` and `topCities`. Update the call site in
`AnalyticsService` to pass empty lists as a placeholder:

```java
return new AnalyticsResponse(totalClicks, clicksOverTime, topReferrers, topUserAgents,
        List.of(), List.of());
```

Task 3.5 will replace `List.of()` with real data.

**Verify:** `cd backend && ./mvnw compile` — no errors.
Empty lists must serialize as `[]` (not null). Confirm by checking that default Jackson
serialization of `List.of()` produces `[]`.

---

## After all three subagents finish

```bash
cd backend && ./mvnw test
```

Full test suite must stay green.
Commit: `feat: IP extraction, geo repo queries, and DTO extension (Phase 3.1/3.3/3.4)`
