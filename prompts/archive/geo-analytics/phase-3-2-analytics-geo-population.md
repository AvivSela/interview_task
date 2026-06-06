# Phase 3.2 — Populate Geo Fields in `AnalyticsService.logClickAsync`

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
File: `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`

**Prerequisites:**
- Phase 1.2 complete: `ClickAnalytics` has `geoStatus`, `country`, `city`
- Phase 2.3 complete: `GeoResolverService` exists with `resolve(String ip)` returning `GeoResult`
- Phase 3.1 complete: `logClickAsync` now receives the real client IP

## Objective

Wire `GeoResolverService` into the async click-logging path so each `ClickAnalytics`
row is saved with resolved geo data.

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`.

1. Inject `GeoResolverService`:
   ```java
   private final GeoResolverService geoResolverService;
   ```
   (Lombok `@RequiredArgsConstructor` handles injection automatically.)

2. In `logClickAsync`, call `resolve` and set the geo fields before saving:

   ```java
   @Async("analyticsTaskExecutor")
   @Transactional
   public void logClickAsync(String shortCode, String referer, String userAgent, String ip) {
       var geo = geoResolverService.resolve(ip);
       clickRepo.save(ClickAnalytics.builder()
           .shortCode(shortCode)
           .referer(referer)
           .userAgent(userAgent)
           .ipAddress(ip)
           .geoStatus(geo.status())
           .country(geo.country())
           .city(geo.city())
           .build());
   }
   ```

Do not change any other methods or add any extra logic.

## Verify

### Unit-level

Run the full test suite — no existing tests should break:
```bash
cd backend && ./mvnw test
```

### Manual integration check

Start the app with `GEO_DB_PATH=src/test/resources/GeoLite2-City-Test.mmdb`.
Issue a redirect request simulating a public test IP (e.g., via curl with
`-H "X-Real-IP: 81.2.69.142"`).
Query the DB directly:
```sql
SELECT geo_status, country, city FROM click_analytics ORDER BY id DESC LIMIT 1;
```
Expected: `geo_status = RESOLVED`, `country` non-null.

## Commit

`feat: wire GeoResolverService into logClickAsync for geo persistence (Phase 3.2)`
