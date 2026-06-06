# Fix 5 — Skip geo DB queries when geo is disabled

## Context

**Prerequisites:** Fixes #10, #8, #7, #6, #2 applied.

`backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java:66`

`getAnalytics()` always issues `topCountries` and `topCities` queries. When `GEO_DB_PATH` is unset (dev, CI), all `country`/`city` columns are `NULL`, so both queries return empty result sets every time — two unnecessary DB round-trips per analytics request.

`GeoResolverService` has no way to signal whether geo is active.

## Objective

Add `isEnabled()` to `GeoResolverService` and gate the two geo query blocks in `AnalyticsService.getAnalytics()` behind it.

> **Note:** This fix uses the current verbose lambda style. Fix #4 (runs immediately after) will extract the `str`/`count` helpers across the whole method.

## Implementation

### 1. `GeoResolverService.java`

Add after the `resolve()` method:

```java
public boolean isEnabled() {
    return reader != null;
}
```

### 2. `AnalyticsService.java`

Replace the `topCountries` block (currently lines 66–71):

```java
List<AnalyticsResponse.CountryCount> topCountries = geoResolverService.isEnabled()
    ? clickRepo.topCountries(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CountryCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? ((Number) row[1]).longValue() : 0L))
        .toList()
    : List.of();
```

Replace the `topCities` block (currently lines 73–79):

```java
List<AnalyticsResponse.CityCount> topCities = geoResolverService.isEnabled()
    ? clickRepo.topCities(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CityCount(
            row[0] != null ? row[0].toString() : "",
            row[1] != null ? row[1].toString() : "",
            row[2] != null ? ((Number) row[2]).longValue() : 0L))
        .toList()
    : List.of();
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: skip geo DB queries when geo resolver is disabled (#5)`
