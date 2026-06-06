# Fix 7 — Remove dead `PENDING` geo status + V3 migration

## Context

**Prerequisites:** Fix #10 and fix #8 applied.

`backend/src/main/java/com/avivly/urlshortener/model/GeoStatus.java:4`

`GeoStatus.PENDING` is never persisted. `AnalyticsService.logClickAsync` always resolves geo before calling `clickRepo.save()`, so no row is ever inserted with `geo_status = 'PENDING'`. Both the Java enum value and the `V2` migration's `DEFAULT 'PENDING'` are dead weight that mislead future queries and dashboards.

The DB column is `VARCHAR(20)`, **not** a PostgreSQL enum type, so removing the value only requires updating the default and backfilling stray rows.

## Objective

1. Remove `PENDING` from `GeoStatus`.
2. Change `ClickAnalytics`'s `@Builder.Default` from `PENDING` to `DISABLED`.
3. Add a V3 Flyway migration that fixes the DB column default and backfills any `PENDING` rows.

## Implementation

### 1. `GeoStatus.java`

Remove the `PENDING` line. Final enum:

```java
package com.avivly.urlshortener.model;

public enum GeoStatus {
    RESOLVED,        // country/city successfully populated
    PRIVATE,         // RFC-1918 / loopback address — no lookup performed
    NOT_FOUND,       // public IP not present in the MaxMind DB
    ERROR,           // lookup attempted but threw an unexpected exception
    DISABLED,        // geo database not configured — no lookup attempted
    DATA_INCOMPLETE  // IP found in MaxMind DB but country/city data absent
}
```

### 2. `ClickAnalytics.java`

Change the `@Builder.Default` field:

```java
@Builder.Default
private GeoStatus geoStatus = GeoStatus.DISABLED;
```

### 3. New migration file

Create `backend/src/main/resources/db/migration/V3__remove_pending_geo_status.sql`:

```sql
ALTER TABLE click_analytics ALTER COLUMN geo_status SET DEFAULT 'DISABLED';
UPDATE click_analytics SET geo_status = 'DISABLED' WHERE geo_status = 'PENDING';
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed. The migration runs at integration-test time — no standalone DB needed now.

## Commit

`fix: remove dead PENDING geo status and add V3 migration (#7)`
