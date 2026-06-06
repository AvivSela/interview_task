# PR Review Fix Plan — geo-feature branch

Ten findings from the automated code review. Each section covers what to change, where, and the exact diff shape to apply.

---

## 1. `application.yml:20` — `include-message: never` silences validation errors in the frontend

**Root cause.** `LinkForm.jsx:94` reads `err.response?.data?.message` and shows it as the validation error text. With `include-message: never`, Spring strips the `message` field from all 4xx responses, so every validation failure falls through to the generic "Something went wrong." string.

**Fix.** Keep `include-message: never` (it prevents accidental exposure of exception messages from unexpected code paths). Instead, add a `@ControllerAdvice` that intercepts validation exceptions and writes an explicit `{"message": "..."}` body.

```java
// NEW FILE: backend/src/main/java/com/avivly/urlshortener/config/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return Map.of("message", msg);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("message", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }
}
```

No change needed in `application.yml` or `LinkForm.jsx`.

---

## 2. `GeoConfig.java:28` — Docker silently creates a directory at the mount path

**Root cause.** When `./geo/GeoLite2-City.mmdb` does not exist on the host, Docker bind-mount creates a *directory* at that path. `file.exists()` returns `true`, the "not found" branch is skipped, and `DatabaseReader.Builder(file).build()` throws `IOException` — degrading geo with a generic "Failed to open MaxMind DB" message instead of a clear "path is a directory" message.

**Fix.** Add an `isDirectory()` guard between the blank-path and the `exists()` checks.

```java
// GeoConfig.java — inside geoDbReader(), after the blank check and before the IOException try
File file = new File(path);
if (file.isDirectory()) {
    log.warn("geo.db.path points to a directory, not a file: {} — geo resolution disabled (Docker created a directory at this mount point?)", path);
    return null;
}
if (!file.exists()) {
    log.warn("MaxMind DB not found at {} — geo resolution disabled", path);
    return null;
}
```

---

## 3. `AnalyticsService.java:25` — `@Async + @Transactional` holds a JDBC connection during mmdb file I/O

**Root cause.** `@Transactional` acquires a JDBC connection the moment `logClickAsync` is entered. It holds that connection open while `geoResolverService.resolve()` performs a synchronous `DatabaseReader.city()` call (file I/O). With a 10-thread pool, 10 concurrent slow reads exhaust the HikariCP pool and stall redirect requests.

**Fix.** Remove `@Transactional` from `logClickAsync`. `clickRepo.save()` runs its own transaction (Spring Data `SimpleJpaRepository.save` is `@Transactional` by default). The geo lookup completes before `save()` is called, so no outer transaction is needed.

```java
// AnalyticsService.java
@Async("analyticsTaskExecutor")
// @Transactional  ← remove
public void logClickAsync(String shortCode, String referer, String userAgent, String ip) {
    var geo = geoResolverService.resolve(ip);   // file I/O — outside any DB connection
    clickRepo.save(ClickAnalytics.builder()     // opens+closes its own transaction
        ...
    );
}
```

---

## 4. `AnalyticsService.java:47` — five copy-pasted null-guard + cast lambdas

**Root cause.** The `row[n] != null ? ((Number) row[n]).longValue() : 0L` pattern appears five times independently. A type change in one query (e.g., PostgreSQL JDBC returning `BigDecimal` instead of `Long`) must be updated everywhere or a `ClassCastException` surfaces at runtime.

**Fix.** Extract two private static helpers in `AnalyticsService`:

```java
private static String str(Object[] r, int i) {
    return r[i] != null ? r[i].toString() : "";
}

private static long count(Object[] r, int i) {
    return r[i] != null ? ((Number) r[i]).longValue() : 0L;
}
```

Then each stream lambda reduces to one line:

```java
.map(row -> new AnalyticsResponse.DailyCount(str(row, 0), count(row, 1)))
.map(row -> new AnalyticsResponse.ReferrerCount(str(row, 0), count(row, 1)))
.map(row -> new AnalyticsResponse.AgentCount(str(row, 0), count(row, 1)))
.map(row -> new AnalyticsResponse.CountryCount(str(row, 0), count(row, 1)))
.map(row -> new AnalyticsResponse.CityCount(str(row, 0), str(row, 1), count(row, 2)))
```

---

## 5. `AnalyticsService.java:66` — geo queries issued even when geo is disabled

**Root cause.** `topCountries` and `topCities` are queried on every `getAnalytics()` call. When `GEO_DB_PATH` is unset (dev, CI), all `country`/`city` columns are `NULL`, so both queries always return empty result sets — two unnecessary DB round-trips per analytics request.

**Fix.** Add `isEnabled()` to `GeoResolverService` and skip the two queries when geo is off.

```java
// GeoResolverService.java — add:
public boolean isEnabled() {
    return reader != null;
}
```

```java
// AnalyticsService.java — replace the two geo query blocks:
List<AnalyticsResponse.CountryCount> topCountries = geoResolverService.isEnabled()
    ? clickRepo.topCountries(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CountryCount(str(row, 0), count(row, 1))).toList()
    : List.of();

List<AnalyticsResponse.CityCount> topCities = geoResolverService.isEnabled()
    ? clickRepo.topCities(shortCode, 10).stream()
        .map(row -> new AnalyticsResponse.CityCount(str(row, 0), str(row, 1), count(row, 2))).toList()
    : List.of();
```

---

## 6. `RedirectController.java:55` — private-IP predicate duplicated in two classes

**Root cause.** `RedirectController.extractClientIp()` and `GeoResolverService.resolve()` both independently check `isSiteLocalAddress || isLoopbackAddress || isLinkLocalAddress`. If one is extended (e.g., to cover carrier-grade NAT `100.64.0.0/10`, which `isSiteLocalAddress` misses), the other silently diverges.

**Fix.** Extract to a package-private utility:

```java
// NEW FILE: backend/src/main/java/com/avivly/urlshortener/util/IpUtils.java
public final class IpUtils {
    private IpUtils() {}

    public static boolean isPrivateAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress();
    }
}
```

Replace both inline predicates with `IpUtils.isPrivateAddress(addr)`.

---

## 7. `GeoStatus.java:3` — `PENDING` is never persisted

**Root cause.** `logClickAsync` always resolves geo before saving, so no row ever has `geo_status = 'PENDING'`. `ClickAnalytics` uses `@Builder.Default` of `GeoStatus.PENDING` as a Java-side default, and V2 migration sets the DB column default to `'PENDING'` — but both are overwritten before any `INSERT`. The enum value and the DB default are dead weight that will mislead future queries or filters.

**Fix.** Remove `PENDING` from the Java enum, change the Java-side default and the DB column default to `DISABLED`.

```java
// GeoStatus.java — remove PENDING:
public enum GeoStatus {
    RESOLVED, PRIVATE, NOT_FOUND, ERROR, DISABLED
    // DATA_INCOMPLETE added by fix #8
}
```

```java
// ClickAnalytics.java — change Builder.Default:
@Builder.Default
private GeoStatus geoStatus = GeoStatus.DISABLED;
```

```sql
-- NEW FILE: db/migration/V3__remove_pending_geo_status.sql
ALTER TABLE click_analytics ALTER COLUMN geo_status SET DEFAULT 'DISABLED';
UPDATE click_analytics SET geo_status = 'DISABLED' WHERE geo_status = 'PENDING';
```

---

## 8. `GeoResolverService.java:34` — null country name conflated with `NOT_FOUND`

**Root cause.** MaxMind's GeoLite2 free tier contains records where the country field is `null` for some IP allocations. The current code returns `GeoResult.notFound()` for these — making them indistinguishable from IPs simply absent from the database. The `RESOLVED` success rate metric cannot distinguish data-quality gaps from missing coverage.

**Fix.** Add a `DATA_INCOMPLETE` status and return it when the record exists but the country is `null`.

```java
// GeoStatus.java — add:
DATA_INCOMPLETE  // IP found in MaxMind DB but country/city data absent
```

```java
// GeoResult.java — add:
public static GeoResult dataIncomplete() {
    return new GeoResult(GeoStatus.DATA_INCOMPLETE, null, null);
}
```

```java
// GeoResolverService.java — change the null-country branch:
String countryName = response.getCountry().getName();
if (countryName == null) {
    return GeoResult.dataIncomplete();   // was: return GeoResult.notFound()
}
```

```sql
-- V3 migration (same file as fix #7):
-- no constraint change needed; geo_status is VARCHAR, not a DB enum type
```

---

## 9. `nginx/nginx.conf:12` — `proxy_set_header` duplicated across two location blocks

**Root cause.** The four `proxy_set_header` directives are copy-pasted into both the `/api/` block and the short-code regex block. Any header change (e.g., adding `X-Request-ID` for distributed tracing) must be applied in both places; missing one silently breaks the other traffic class.

**Fix.** Move the headers to the `server` block. nginx applies `server`-level `proxy_set_header` directives to all descendant `location` blocks that do not override them.

```nginx
server {
    listen 80;

    # Applied to all proxy_pass locations below
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    location /api/ {
        proxy_pass http://backend:8080;
        # headers inherited from server block
    }

    location ^~ /assets/ {
        proxy_pass http://frontend:80;
    }

    location ~ ^/[A-Za-z0-9_-]+ {
        limit_req zone=redirects burst=5 nodelay;
        proxy_pass http://backend:8080;
        # headers inherited from server block
    }

    location / {
        proxy_pass http://frontend:80;
    }
}
```

---

## 10. `GeoResult.java:16` — failure factory methods allocate a new instance on every call

**Root cause.** `GeoResult.disabled()`, `private_()`, `notFound()`, and `error()` each call `new GeoResult(...)` on every invocation. At high click volume with geo disabled, `GeoResult.disabled()` produces thousands of short-lived objects per second that immediately become garbage on the async executor threads.

**Fix.** Replace with pre-allocated `static final` constants. `GeoResult` is a record (immutable value type), so sharing instances is safe.

```java
// GeoResult.java
public record GeoResult(GeoStatus status, @Nullable String country, @Nullable String city) {

    public static final GeoResult PRIVATE       = new GeoResult(GeoStatus.PRIVATE, null, null);
    public static final GeoResult NOT_FOUND     = new GeoResult(GeoStatus.NOT_FOUND, null, null);
    public static final GeoResult ERROR         = new GeoResult(GeoStatus.ERROR, null, null);
    public static final GeoResult DISABLED      = new GeoResult(GeoStatus.DISABLED, null, null);
    public static final GeoResult DATA_INCOMPLETE = new GeoResult(GeoStatus.DATA_INCOMPLETE, null, null);

    public static GeoResult private_()        { return PRIVATE; }
    public static GeoResult notFound()        { return NOT_FOUND; }
    public static GeoResult error()           { return ERROR; }
    public static GeoResult disabled()        { return DISABLED; }
    public static GeoResult dataIncomplete()  { return DATA_INCOMPLETE; }

    public static GeoResult resolved(@Nullable String country, @Nullable String city) {
        return new GeoResult(GeoStatus.RESOLVED, country, city);
    }
}
```

Callers require no changes — they still use the factory methods.

---

## Dependency order for implementation

Apply in this order to avoid compile errors mid-way:

1. **#10** — add constants to `GeoResult` (no other deps)
2. **#8** — add `DATA_INCOMPLETE` to `GeoStatus` and `GeoResult.dataIncomplete()`
3. **#7** — remove `PENDING` from `GeoStatus`; add V3 migration (combine with #8's migration note)
4. **#6** — add `IpUtils`; update both callers
5. **#2** — update `GeoConfig`
6. **#5** — add `GeoResolverService.isEnabled()`; update `AnalyticsService.getAnalytics()`
7. **#4** — extract `str/count` helpers in `AnalyticsService`
8. **#3** — remove `@Transactional` from `logClickAsync`
9. **#9** — update `nginx.conf`
10. **#1** — add `GlobalExceptionHandler`
