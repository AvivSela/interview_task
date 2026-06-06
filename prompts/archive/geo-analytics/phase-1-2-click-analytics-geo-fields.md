# Phase 1.2 — Add Geo Fields to `ClickAnalytics`

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Entity: `backend/src/main/java/com/avivly/urlshortener/model/ClickAnalytics.java`

Current fields: `id`, `shortCode`, `clickedAt`, `referer`, `userAgent`, `ipAddress`.
Uses Lombok `@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`.

**Prerequisite:** Phase 1.1 complete — `GeoStatus` enum must exist.

## Objective

Add three geo-resolution fields to `ClickAnalytics` so the async geo lookup can
persist its result alongside each click record.

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/model/ClickAnalytics.java`.

Add the import:
```java
import com.avivly.urlshortener.model.GeoStatus;
```

Add these three fields after the `ipAddress` field:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
@Builder.Default
private GeoStatus geoStatus = GeoStatus.PENDING;

@Column(length = 100)
private String country;

@Column(length = 100)
private String city;
```

Also add the missing import for `EnumType`:
```java
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
```

Do not change any other fields, annotations, or methods.

## Verify

```bash
cd backend && ./mvnw test
```

Full test suite must stay green. Existing entity tests must still pass.

## Commit

`feat: add geoStatus, country, city fields to ClickAnalytics (Phase 1.2)`
