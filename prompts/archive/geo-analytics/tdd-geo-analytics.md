# Technical Design Document

**Feature:** IP Geolocation Analytics for Shortened Link Clicks
**Project:** Avivly URL Shortener
**Author:** Aviv
**Date:** 2026-06-03
**Status:** Draft
**PRD:** `prd-geo-analytics.md`

---

## 1. Overview

This document describes the implementation plan for geo-enriched click analytics. Each click is asynchronously resolved to a Country + City via the MaxMind GeoLite2-City `.mmdb` database and stored on the `ClickAnalytics` record. The analytics API and dashboard surface top-10 country and city breakdowns.

The feature touches: the `ClickAnalytics` entity, `AnalyticsService`, `RedirectController`, `ClickAnalyticsRepository`, `AnalyticsResponse` DTO, the Spring configuration layer (new beans), Spring Boot health indicators, and the `AnalyticsPanel` frontend component.

---

## 2. Architecture Overview

```
HTTP Request
    │
    ▼
RedirectController.redirect()
    │  extracts real client IP (X-Real-IP → XFF → remoteAddr)
    │  issues 302 immediately
    │
    └──► analyticsService.logClickAsync(shortCode, ip, referer, ua)   [async / analytics-* pool]
              │
              ├──► geoResolverService.resolve(ip)                      [same thread]
              │         │  checks private/loopback → GeoStatus.PRIVATE
              │         │  looks up .mmdb → GeoStatus.RESOLVED / NOT_FOUND / ERROR
              │         └──► returns GeoResult(country, city, status)
              │
              └──► clickAnalyticsRepository.save(click)               [geo fields populated]


GET /api/links/{shortCode}/analytics
    │
    ├──► clickAnalyticsRepository.topCountries(shortCode, limit)
    ├──► clickAnalyticsRepository.topCities(shortCode, limit)
    └──► AnalyticsResponse (topCountries[], topCities[])
```

The `DatabaseReader` (MaxMind) is held as a singleton Spring `@Bean` in `GeoConfig`. If the `.mmdb` file is absent or corrupt on startup, the bean is `null`; `GeoResolverService` operates in degraded mode, writing `GeoStatus.ERROR` for every lookup; and `GeoResolverHealthIndicator` reports `DOWN`.

---

## 3. Data Model Changes

### 3.1 `ClickAnalytics` Entity

**File:** `model/ClickAnalytics.java`

Add three fields and a new enum type reference:

```java
// new enum — see section 4
@Enumerated(EnumType.STRING)
@Column(name = "geo_status", nullable = false, length = 20)
@Builder.Default
private GeoStatus geoStatus = GeoStatus.PENDING;

@Column(name = "country", length = 100)
private String country;

@Column(name = "city", length = 100)
private String city;
```

**Schema impact:** With the current `hibernate.ddl-auto: update`, Hibernate will `ALTER TABLE click_analytics ADD COLUMN` automatically on next startup. The PRD's three indexes and the migration to Flyway are addressed in §3.3.

### 3.2 `GeoStatus` Enum

**New file:** `model/GeoStatus.java`

```java
package com.avivly.urlshortener.model;

public enum GeoStatus {
    PENDING,      // pre-feature rows / not yet processed
    RESOLVED,     // country (and optionally city) found
    PRIVATE,      // loopback or RFC 1918 — no lookup attempted
    NOT_FOUND,    // public IP absent from GeoLite2 DB
    ERROR         // lookup threw an exception
}
```

### 3.3 Database Indexes & Migration Strategy

The project currently uses `hibernate.ddl-auto: update`. Introducing Flyway is the right call for production correctness (idempotent, versioned, auditable), and the PRD explicitly calls for it. The migration also adds indexes that Hibernate auto-schema does not generate automatically.

**Action:** Add Flyway to `pom.xml` and set `spring.jpa.hibernate.ddl-auto: validate`. Write the first versioned migration:

```
src/main/resources/db/migration/V1__baseline.sql      ← baseline of current schema (generated once)
src/main/resources/db/migration/V2__geo_analytics.sql ← this feature
```

**`V2__geo_analytics.sql`:**

```sql
ALTER TABLE click_analytics
    ADD COLUMN IF NOT EXISTS country    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS city       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS geo_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_click_analytics_short_code
    ON click_analytics (short_code);

CREATE INDEX IF NOT EXISTS idx_click_analytics_short_code_country
    ON click_analytics (short_code, country);

CREATE INDEX IF NOT EXISTS idx_click_analytics_short_code_city
    ON click_analytics (short_code, city);
```

> **Flyway dependency** to add to `pom.xml`:
> ```xml
> <dependency>
>     <groupId>org.flywaydb</groupId>
>     <artifactId>flyway-core</artifactId>
> </dependency>
> <dependency>
>     <groupId>org.flywaydb</groupId>
>     <artifactId>flyway-database-postgresql</artifactId>
> </dependency>
> ```
> Set `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1` so the baseline migration applies cleanly on an existing DB.

---

## 4. New Components

### 4.1 `GeoResult` Record

**New file:** `dto/GeoResult.java`

```java
package com.avivly.urlshortener.dto;

import com.avivly.urlshortener.model.GeoStatus;

public record GeoResult(String country, String city, GeoStatus status) {

    public static GeoResult private_() {
        return new GeoResult(null, null, GeoStatus.PRIVATE);
    }

    public static GeoResult notFound() {
        return new GeoResult(null, null, GeoStatus.NOT_FOUND);
    }

    public static GeoResult error() {
        return new GeoResult(null, null, GeoStatus.ERROR);
    }

    public static GeoResult resolved(String country, String city) {
        return new GeoResult(country, city, GeoStatus.RESOLVED);
    }
}
```

### 4.2 `GeoConfig`

**New file:** `config/GeoConfig.java`

Loads the `.mmdb` file once at startup. Returns `null` if the file is missing or corrupt so the rest of the application can start in degraded mode.

```java
@Configuration
@Slf4j
public class GeoConfig {

    @Value("${geo.db.path:#{null}}")
    private String dbPath;

    @Bean
    @Nullable
    public DatabaseReader databaseReader() {
        if (dbPath == null || dbPath.isBlank()) {
            log.warn("geo.db.path not configured — geo resolution disabled");
            return null;
        }
        File file = new File(dbPath);
        if (!file.exists()) {
            log.warn("GeoLite2 database not found at {} — geo resolution disabled", dbPath);
            return null;
        }
        try {
            return new DatabaseReader.Builder(file).build();
        } catch (Exception e) {
            log.error("Failed to open GeoLite2 database — geo resolution disabled", e);
            return null;
        }
    }
}
```

**`application.yml` addition:**

```yaml
geo:
  db:
    path: ${GEO_DB_PATH:}   # e.g. /data/GeoLite2-City.mmdb
```

### 4.3 `GeoResolverService`

**New file:** `service/GeoResolverService.java`

Encapsulates all MaxMind interaction. Called synchronously from the analytics thread (redirect is not blocked).

```java
@Service
@Slf4j
public class GeoResolverService {

    private final DatabaseReader reader;   // null in degraded mode

    public GeoResolverService(@Nullable DatabaseReader reader) {
        this.reader = reader;
    }

    public GeoResult resolve(String ip) {
        if (ip == null || ip.isBlank()) return GeoResult.error();
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (isPrivate(addr)) return GeoResult.private_();
            if (reader == null) return GeoResult.error();

            CityResponse response = reader.city(addr);
            String country = response.getCountry().getName();
            String city    = response.getCity().getName();
            log.debug("Geo lookup — ip={}, country={}, city={}",
                      mask(ip), country, city);
            return GeoResult.resolved(country, city);

        } catch (AddressNotFoundException e) {
            return GeoResult.notFound();
        } catch (Exception e) {
            log.warn("Geo lookup failed for ip={}: {}", mask(ip), e.getMessage());
            return GeoResult.error();
        }
    }

    private boolean isPrivate(InetAddress addr) {
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()   // RFC 1918: 10/8, 172.16/12, 192.168/16
            || addr.isLinkLocalAddress();   // 169.254/16
    }

    private String mask(String ip) {
        // replace last octet with "xxx" for IPv4; omit last group for IPv6
        int lastDot = ip.lastIndexOf('.');
        return lastDot >= 0 ? ip.substring(0, lastDot) + ".xxx" : ip.replaceAll("[^:]+$", "xxx");
    }
}
```

**IP privacy rule:** No raw IP ever reaches a log statement. Only `mask(ip)` output is logged.

### 4.4 `GeoResolverHealthIndicator`

**New file:** `config/GeoResolverHealthIndicator.java`

```java
@Component
public class GeoResolverHealthIndicator implements HealthIndicator {

    private final DatabaseReader reader;

    public GeoResolverHealthIndicator(@Nullable DatabaseReader reader) {
        this.reader = reader;
    }

    @Override
    public Health health() {
        if (reader == null) {
            return Health.down()
                .withDetail("reason", "GeoLite2 database unavailable")
                .build();
        }
        return Health.up().build();
    }
}
```

Requires `spring-boot-starter-actuator` in `pom.xml` (add if not already present) and the `/actuator/health` endpoint exposed.

---

## 5. Modified Components

### 5.1 `RedirectController` — Real IP Extraction

**File:** `controller/RedirectController.java`

Replace the direct `request.getRemoteAddr()` call with a helper that implements the priority chain from the PRD:

```java
private String extractClientIp(HttpServletRequest request) {
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) return realIp.trim();

    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        String[] parts = xff.split(",");
        // right-to-left: first non-private entry wins
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!isPrivateOrLoopback(candidate)) return candidate;
        }
    }

    return request.getRemoteAddr();
}
```

Pass the extracted IP to `analyticsService.logClickAsync()`.

### 5.2 `AnalyticsService` — Geo Population

**File:** `service/AnalyticsService.java`

Update `logClickAsync()` to call the resolver and populate geo fields before saving:

```java
@Async("analyticsTaskExecutor")
public void logClickAsync(String shortCode, String ip, String referer, String userAgent) {
    GeoResult geo = geoResolverService.resolve(ip);

    ClickAnalytics click = ClickAnalytics.builder()
        .shortCode(shortCode)
        .ipAddress(ip)           // privacy decision pending — see §9
        .referer(referer)
        .userAgent(userAgent)
        .country(geo.country())
        .city(geo.city())
        .geoStatus(geo.status())
        .build();

    clickAnalyticsRepository.save(click);
}
```

Update `getAnalytics()` to populate the new DTO fields:

```java
public AnalyticsResponse getAnalytics(String shortCode) {
    // existing fields ...
    List<CountryCount> topCountries = clickAnalyticsRepository
        .topCountries(shortCode, 10).stream()
        .map(r -> new CountryCount((String) r[0], ((Number) r[1]).longValue()))
        .toList();

    List<CityCount> topCities = clickAnalyticsRepository
        .topCities(shortCode, 10).stream()
        .map(r -> new CityCount((String) r[0], (String) r[1], ((Number) r[2]).longValue()))
        .toList();

    return new AnalyticsResponse(totalClicks, clicksByDay, topReferrers, topUserAgents,
                                 topCountries, topCities);
}
```

### 5.3 `ClickAnalyticsRepository` — Aggregate Queries

**File:** `repository/ClickAnalyticsRepository.java`

Use native queries — JPQL does not support `LIMIT`:

```java
@Query(value = "SELECT country, COUNT(*) AS cnt FROM click_analytics " +
               "WHERE short_code = :shortCode AND country IS NOT NULL " +
               "GROUP BY country ORDER BY cnt DESC LIMIT :limit",
       nativeQuery = true)
List<Object[]> topCountries(@Param("shortCode") String shortCode, @Param("limit") int limit);

@Query(value = "SELECT city, country, COUNT(*) AS cnt FROM click_analytics " +
               "WHERE short_code = :shortCode AND city IS NOT NULL " +
               "GROUP BY city, country ORDER BY cnt DESC LIMIT :limit",
       nativeQuery = true)
List<Object[]> topCities(@Param("shortCode") String shortCode, @Param("limit") int limit);
```

### 5.4 `AnalyticsResponse` — New DTO Fields

**File:** `dto/AnalyticsResponse.java`

```java
public record AnalyticsResponse(
    int totalClicks,
    List<DailyCount>    clicksOverTime,
    List<ReferrerCount> topReferrers,
    List<AgentCount>    topUserAgents,
    List<CountryCount>  topCountries,   // NEW
    List<CityCount>     topCities       // NEW
) {
    public record DailyCount(String date, long count) {}
    public record ReferrerCount(String referer, long count) {}
    public record AgentCount(String userAgent, long count) {}
    public record CountryCount(String country, long clicks) {}        // NEW
    public record CityCount(String city, String country, long clicks) {} // NEW
}
```

Backward-compatible: new fields serialize as `[]` by default (Jackson serializes empty lists, not nulls, which is what the existing pattern produces).

---

## 6. Frontend Changes

### 6.1 `AnalyticsPanel.jsx`

Two new horizontal bar charts follow the existing `BarChart` pattern:

```jsx
// Top Countries bar chart
{data.topCountries?.length > 0 && (
  <div>
    <h3 className="text-sm font-medium text-gray-600 mb-1">Top Countries</h3>
    <ResponsiveContainer width="100%" height={Math.min(data.topCountries.length * 32, 320)}>
      <BarChart data={data.topCountries} layout="vertical">
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
        <YAxis type="category" dataKey="country" width={120} tick={{ fontSize: 11 }} />
        <Tooltip />
        <Bar dataKey="clicks" fill="#3b82f6" radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  </div>
)}

// Top Cities bar chart
{data.topCities?.length > 0 && (
  <div>
    <h3 className="text-sm font-medium text-gray-600 mb-1">Top Cities</h3>
    <ResponsiveContainer width="100%" height={Math.min(data.topCities.length * 32, 320)}>
      <BarChart
        data={data.topCities.map(c => ({ ...c, label: `${c.city}, ${c.country}` }))}
        layout="vertical"
      >
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
        <YAxis type="category" dataKey="label" width={160} tick={{ fontSize: 11 }} />
        <Tooltip />
        <Bar dataKey="clicks" fill="#10b981" radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  </div>
)}

// Empty-state placeholder — shown when both arrays are empty
{data.topCountries?.length === 0 && data.topCities?.length === 0 && (
  <p className="text-sm text-gray-400 italic">Geographic data not yet available for this link.</p>
)}
```

No new dependencies — `recharts` is already in `package.json`.

---

## 7. Configuration & Dependency Changes

### 7.1 `pom.xml` Additions

```xml
<!-- GeoIP2 SDK -->
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.2.0</version>
</dependency>

<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Actuator (for HealthIndicator) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 7.2 `application.yml` Additions

```yaml
geo:
  db:
    path: ${GEO_DB_PATH:}

spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
  jpa:
    hibernate:
      ddl-auto: validate    # switch from 'update' once Flyway is in place

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

### 7.3 CI / Docker

- **Docker:** Mount the `.mmdb` file via a volume: `-v /host/path/GeoLite2-City.mmdb:/data/GeoLite2-City.mmdb` and set `GEO_DB_PATH=/data/GeoLite2-City.mmdb`.
- **CI:** Download the database using a MaxMind license key secret before running integration tests. Suggested step:
  ```yaml
  - name: Download GeoLite2-City
    env:
      MAXMIND_LICENSE_KEY: ${{ secrets.MAXMIND_LICENSE_KEY }}
    run: |
      curl -sL "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=${MAXMIND_LICENSE_KEY}&suffix=tar.gz" \
        | tar xz --strip-components=1 --wildcards '*.mmdb' -C src/test/resources/
  ```
- **`.gitignore`:** Add `*.mmdb` with a carve-out for the committed test fixture (see §7.4).
- **MaxMind attribution** must appear in the app's About/legal page (EULA requirement).

### 7.4 Local Development (no per-clone download)

The MaxMind test database is licensed under Apache 2.0 — unlike the production DB, it **can be committed to the repo**. Reuse the same file added for unit tests as the default geo DB for the `dev` Spring profile. This gives every developer realistic `RESOLVED` rows out of the box without downloading anything.

**`application-dev.yml`** (new file):

```yaml
geo:
  db:
    path: src/test/resources/GeoLite2-City-Test.mmdb
```

The test DB covers a small fixed set of IPs (e.g. `2.125.160.216` → United Kingdom, `81.2.69.142` → United Kingdom). Any click from those IPs during manual testing will produce a real `RESOLVED` record; all other IPs fall through to `NOT_FOUND`, which is still a valid non-error state. The health indicator reports `UP` because the reader is non-null.

**`.gitignore` update** — exclude production DBs but keep the test fixture:

```gitignore
*.mmdb
!src/test/resources/GeoLite2-City-Test.mmdb
```

No action needed for the `prod` profile — `GEO_DB_PATH` is set via Docker volume or the CI download step (§7.3).

---

## 8. Testing Plan

### 8.1 Unit Tests

| Class | Test | Notes |
|-------|------|-------|
| `GeoResolverService` | Public IP resolves to `RESOLVED` | Use a small test `.mmdb` (MaxMind provides one) |
| `GeoResolverService` | `10.x.x.x` → `PRIVATE` | No reader call |
| `GeoResolverService` | `127.0.0.1` → `PRIVATE` | |
| `GeoResolverService` | Unknown public IP → `NOT_FOUND` | |
| `GeoResolverService` | `reader == null` → `ERROR` | Degraded mode |
| `GeoResolverService` | `mask()` produces no raw last octet | Log privacy |
| `RedirectController` | `X-Real-IP` header wins | Mock request with header |
| `RedirectController` | XFF right-to-left, skip private | Multi-entry XFF |
| `RedirectController` | Falls back to `remoteAddr` | No headers |

**Test `.mmdb`:** Add `src/test/resources/GeoLite2-City-Test.mmdb` (MaxMind's public test database). Because `GeoConfig` opens the file via `new File(dbPath)`, the path must be a real filesystem path, not a `classpath:` URI. Resolve it at test time with:

```java
@TestPropertySource(properties =
    "geo.db.path=src/test/resources/GeoLite2-City-Test.mmdb")
```

Or inject the absolute path via a `@BeforeAll` / `ApplicationContextInitializer` that resolves `ClassPathResource("GeoLite2-City-Test.mmdb").getFile().getAbsolutePath()`.

### 8.2 Integration Tests

| Scenario | Verification |
|----------|-------------|
| Click from public test IP | `country` non-null, `geo_status = RESOLVED` in DB |
| Click from `127.0.0.1` | `geo_status = PRIVATE`, no country/city |
| Missing `.mmdb` on startup | App starts, health = `DOWN`, redirect still works |
| `GET /api/links/{code}/analytics` | `topCountries` and `topCities` arrays present |
| Analytics empty state | Both arrays `[]` when no geo data |

Extend the existing H2-based integration test suite. Note: H2 in PostgreSQL-compat mode should handle the native queries; if not, use `@DataJpaTest` with Testcontainers (PostgreSQL).

### 8.3 Acceptance Criteria Coverage

All AC-01 through AC-10 from the PRD are covered by unit + integration tests above. AC-07 (no raw IPs in logs) validated by asserting `mask()` output in log capture.

---

## 9. Privacy Gate (AC per PRD §6)

Raw `ipAddress` storage is **deferred** — a decision must be made before this feature ships to production handling EU traffic. The implementation writes the raw IP to `ClickAnalytics.ipAddress` as today; whichever option is chosen (drop, truncate, retention window) will require a follow-up migration and a change to `logClickAsync`. This TDD does not pre-implement that decision.

---

## 10. Open Questions

| # | Question | Owner | Blocking? |
|---|----------|-------|-----------|
| OQ-01 | nginx config to forward `X-Forwarded-For` / `X-Real-IP` — who owns this? | DevOps | Needed for end-to-end production testing |
| OQ-02 | "Show all" expansion for countries/cities beyond top-10? | Product | Needed before final UI implementation |
| OQ-03 | Should `GeoLite2-City.mmdb` hot-reload (watched path) or require restart? | Engineering | Nice-to-have; restart acceptable for v1 |
| OQ-04 | Will `ddl-auto: validate` break the dev environment before the Flyway baseline is generated? | Engineering | Needs a one-time baseline export from existing schema |
| OQ-05 | Is `spring-boot-starter-actuator` already in scope or does its addition require a security review of exposed endpoints? | Engineering | Before adding actuator dependency |

---

## 11. File Change Summary

| File | Type | Change |
|------|------|--------|
| `model/ClickAnalytics.java` | Modify | Add `country`, `city`, `geoStatus` fields |
| `model/GeoStatus.java` | **New** | Enum: `PENDING / RESOLVED / PRIVATE / NOT_FOUND / ERROR` |
| `dto/GeoResult.java` | **New** | Record returned by `GeoResolverService` |
| `dto/AnalyticsResponse.java` | Modify | Add `CountryCount`, `CityCount` inner records + list fields |
| `config/GeoConfig.java` | **New** | Spring `@Bean` loading `DatabaseReader`; degraded-mode null |
| `config/GeoResolverHealthIndicator.java` | **New** | `HealthIndicator` reporting `DOWN` when reader is null |
| `service/GeoResolverService.java` | **New** | MaxMind lookup + IP masking + PRIVATE check |
| `service/AnalyticsService.java` | Modify | Wire `GeoResolverService`; populate geo fields in `logClickAsync`; populate DTO in `getAnalytics` |
| `controller/RedirectController.java` | Modify | `extractClientIp()` helper (XRI → XFF right-to-left → remoteAddr) |
| `repository/ClickAnalyticsRepository.java` | Modify | Add `topCountries()` and `topCities()` native queries |
| `pom.xml` | Modify | Add `geoip2`, `flyway-core`, `flyway-database-postgresql`, `actuator` |
| `application.yml` | Modify | Add `geo.db.path`, Flyway config, actuator exposure |
| `db/migration/V2__geo_analytics.sql` | **New** | ALTER TABLE + three indexes |
| `db/migration/V1__baseline.sql` | **New** | Baseline snapshot of existing schema |
| `src/test/resources/GeoLite2-City-Test.mmdb` | **New** | MaxMind test fixture (Apache 2.0 — committed to repo) |
| `application-dev.yml` | **New** | Points `geo.db.path` at the test fixture for zero-setup local dev |
| `frontend/src/components/AnalyticsPanel.jsx` | Modify | Two new horizontal bar charts + empty-state placeholder |
| `.gitignore` | Modify | Add `*.mmdb` with `!src/test/resources/GeoLite2-City-Test.mmdb` exception |

---

*End of Document*
