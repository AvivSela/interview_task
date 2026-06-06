# Fix 3 — Remove `@Transactional` from `logClickAsync`

## Context

**Prerequisites:** Fixes #10, #8, #7, #6, #2, #5, #4 applied.

`backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java:23`

`logClickAsync` is annotated with both `@Async` and `@Transactional`. `@Transactional` acquires a JDBC connection the moment the method is entered and holds it open while `geoResolverService.resolve()` performs a synchronous `DatabaseReader.city()` call (file I/O on the async executor thread). With a 10-thread pool, 10 concurrent slow reads exhaust the HikariCP pool and stall redirect requests.

`clickRepo.save()` is provided by Spring Data's `SimpleJpaRepository`, which is `@Transactional` by default — so it opens and closes its own transaction. No outer transaction is needed.

## Objective

Remove `@Transactional` from `logClickAsync`. No other changes.

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`.

Remove the `@Transactional` annotation from `logClickAsync`:

```java
// Before:
@Async("analyticsTaskExecutor")
@Transactional
public void logClickAsync(...)

// After:
@Async("analyticsTaskExecutor")
public void logClickAsync(...)
```

Also remove the now-unused import if `@Transactional` is no longer referenced anywhere else in the file:

```java
import org.springframework.transaction.annotation.Transactional;
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: remove @Transactional from logClickAsync to avoid holding JDBC connection during file I/O (#3)`
