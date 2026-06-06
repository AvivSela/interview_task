# Fix 8 — Add `DATA_INCOMPLETE` geo status

## Context

**Prerequisite:** Fix #10 applied (`GeoResult` uses static constants).

`backend/src/main/java/com/avivly/urlshortener/service/GeoResolverService.java:34`

MaxMind GeoLite2 free tier contains records where the country field is `null` for some IP allocations. Currently `GeoResolverService.resolve()` returns `GeoResult.notFound()` for these — making them indistinguishable from IPs absent from the database entirely. The `RESOLVED` success-rate metric cannot separate data-quality gaps from missing coverage.

## Objective

Add a `DATA_INCOMPLETE` enum value and wire it up across the three layers: `GeoStatus`, `GeoResult`, `GeoResolverService`.

## Implementation

### 1. `GeoStatus.java`

Add `DATA_INCOMPLETE` at the end of the enum:

```java
public enum GeoStatus {
    PENDING,         // not yet resolved (default at insert time)
    RESOLVED,        // country/city successfully populated
    PRIVATE,         // RFC-1918 / loopback address — no lookup performed
    NOT_FOUND,       // public IP not present in the MaxMind DB
    ERROR,           // lookup attempted but threw an unexpected exception
    DISABLED,        // geo database not configured — no lookup attempted
    DATA_INCOMPLETE  // IP found in MaxMind DB but country/city data absent
}
```

> `PENDING` removal happens in fix #7. Leave it in for now.

### 2. `GeoResult.java`

Add the constant and factory **after** the existing `DISABLED` constant and `disabled()` factory:

```java
public static final GeoResult DATA_INCOMPLETE = new GeoResult(GeoStatus.DATA_INCOMPLETE, null, null);

public static GeoResult dataIncomplete() { return DATA_INCOMPLETE; }
```

### 3. `GeoResolverService.java`

Change the null-country branch (currently returns `GeoResult.notFound()`):

```java
String countryName = response.getCountry().getName();
if (countryName == null) {
    return GeoResult.dataIncomplete();   // was: return GeoResult.notFound()
}
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: distinguish DATA_INCOMPLETE geo status from NOT_FOUND (#8)`
