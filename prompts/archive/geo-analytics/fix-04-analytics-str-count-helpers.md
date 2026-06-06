# Fix 4 — Extract `str`/`count` helpers in `AnalyticsService`

## Context

**Prerequisites:** Fixes #10, #8, #7, #6, #2, #5 applied.

`backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`

The null-guard + cast patterns `row[n] != null ? row[n].toString() : ""` and `row[n] != null ? ((Number) row[n]).longValue() : 0L` appear across all five query blocks (daily, referrers, agents, countries, cities). A type change in one query (e.g., PostgreSQL JDBC returning `BigDecimal` instead of `Long`) must be updated everywhere or a `ClassCastException` surfaces at runtime.

## Objective

Extract two `private static` helpers and replace all five lambda bodies. No behaviour change.

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`.

### Step 1 — Add helpers at the bottom of the class (before the closing `}`)

```java
private static String str(Object[] r, int i) {
    return r[i] != null ? r[i].toString() : "";
}

private static long count(Object[] r, int i) {
    return r[i] != null ? ((Number) r[i]).longValue() : 0L;
}
```

### Step 2 — Rewrite the five query stream lambdas

Replace all five `.map(row -> ...)` blocks with the compact forms:

```java
List<AnalyticsResponse.DailyCount> clicksOverTime = clickRepo.countClicksByDay(shortCode)
    .stream()
    .map(row -> new AnalyticsResponse.DailyCount(str(row, 0), count(row, 1)))
    .toList();

List<AnalyticsResponse.ReferrerCount> topReferrers = clickRepo.topReferrers(shortCode, 10)
    .stream()
    .map(row -> new AnalyticsResponse.ReferrerCount(str(row, 0), count(row, 1)))
    .toList();

List<AnalyticsResponse.AgentCount> topUserAgents = clickRepo.topUserAgents(shortCode, 10)
    .stream()
    .map(row -> new AnalyticsResponse.AgentCount(str(row, 0), count(row, 1)))
    .toList();

List<AnalyticsResponse.CountryCount> topCountries = geoResolverService.isEnabled()
    ? clickRepo.topCountries(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CountryCount(str(row, 0), count(row, 1)))
        .toList()
    : List.of();

List<AnalyticsResponse.CityCount> topCities = geoResolverService.isEnabled()
    ? clickRepo.topCities(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CityCount(str(row, 0), str(row, 1), count(row, 2)))
        .toList()
    : List.of();
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: extract str/count helpers in AnalyticsService to centralise null-casts (#4)`
