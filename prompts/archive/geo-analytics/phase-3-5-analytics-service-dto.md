# Phase 3.5 — Populate Geo DTO in `AnalyticsService.getAnalytics`

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
File: `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`

**Prerequisites:**
- Phase 3.3 complete: `topCountries(shortCode, limit)` and `topCities(shortCode, limit)`
  exist in `ClickAnalyticsRepository`
- Phase 3.4 complete: `AnalyticsResponse` has `CountryCount` and `CityCount` inner records,
  and `topCountries` / `topCities` list fields (currently returning `List.of()`)

## Objective

Replace the `List.of()` placeholders in `getAnalytics` with real repository calls
so the analytics endpoint returns populated geo aggregates.

## Implementation

Edit `getAnalytics` in `AnalyticsService`:

```java
public AnalyticsResponse getAnalytics(String shortCode) {
    int totalClicks = linkRepo.findByShortCode(shortCode)
        .map(l -> l.getTotalClicks())
        .orElse(0);

    List<AnalyticsResponse.DailyCount> clicksOverTime = clickRepo.countClicksByDay(shortCode)
        .stream()
        .map(row -> new AnalyticsResponse.DailyCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? ((Number) row[1]).longValue() : 0L))
        .toList();

    List<AnalyticsResponse.ReferrerCount> topReferrers = clickRepo.topReferrers(shortCode)
        .stream()
        .map(row -> new AnalyticsResponse.ReferrerCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? ((Number) row[1]).longValue() : 0L))
        .toList();

    List<AnalyticsResponse.AgentCount> topUserAgents = clickRepo.topUserAgents(shortCode)
        .stream()
        .map(row -> new AnalyticsResponse.AgentCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? ((Number) row[1]).longValue() : 0L))
        .toList();

    List<AnalyticsResponse.CountryCount> topCountries = clickRepo.topCountries(shortCode, 10)
        .stream()
        .map(row -> new AnalyticsResponse.CountryCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? ((Number) row[1]).longValue() : 0L))
        .toList();

    List<AnalyticsResponse.CityCount> topCities = clickRepo.topCities(shortCode, 10)
        .stream()
        .map(row -> new AnalyticsResponse.CityCount(
            row[0] != null ? row[0].toString() : "",   // city
            row[1] != null ? row[1].toString() : "",   // country
            row[2] != null ? ((Number) row[2]).longValue() : 0L))
        .toList();

    return new AnalyticsResponse(totalClicks, clicksOverTime, topReferrers, topUserAgents,
            topCountries, topCities);
}
```

## Verify

### Automated tests

```bash
cd backend && ./mvnw test
```

### Manual check

```bash
curl -s http://localhost:8080/api/links/{shortCode}/analytics | jq '.topCountries, .topCities'
```

- With geo data present: arrays contain entries sorted by `clicks` DESC.
- With no geo data: both arrays return `[]` (not `null`).

## Commit

`feat: populate topCountries and topCities in analytics DTO (Phase 3.5)`
