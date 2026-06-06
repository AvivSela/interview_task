# Code Review — `generate-qr` branch

**Scope:** `git diff master...HEAD` + uncommitted working-tree changes  
**Effort:** high (7 finder angles × 6 candidates → verified)  
**Date:** 2026-06-03

---

## Findings

### 1. CRITICAL — Infinite redirect loop: `302 → /link-expired` matches nginx short-code regex

**File:** `backend/src/main/java/com/avivly/urlshortener/controller/RedirectController.java:29`

The expired/invalid link response was changed from `410 Gone` to `302 Found → /link-expired`. nginx has:

```nginx
location ~ ^/[A-Za-z0-9_-]+ {
    proxy_pass http://backend:8080;
}
```

`/link-expired` matches `[A-Za-z0-9_-]+`. nginx intercepts the follow-up `GET /link-expired`, routes it back to the Spring backend, the backend finds no short code named `link-expired`, issues another `302 → /link-expired`, and the cycle repeats until the browser hits ERR_TOO_MANY_REDIRECTS. The frontend `/link-expired` SPA route in `App.jsx` never gets a chance to render.

**Fix:** Add a `location ^~ /link-expired` block (evaluated before regex locations) that routes to the frontend, or change the backend to return the error page body directly instead of redirecting.

---

### 2. HIGH — `recordClick` is now synchronous: a DB error aborts the redirect with a 500

**File:** `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java:115`

`@Async` was removed from `recordClick`. `RedirectController.redirect()` calls `linkService.recordClick(shortCode)` with no try/catch. If `repo.incrementClicks()` throws (deadlock, connection timeout, `DataAccessException`), the exception propagates through the controller. `GlobalExceptionHandler` only handles `MethodArgumentNotValidException` and `ResponseStatusException` — a raw JPA exception falls through to Spring Boot's default 500 handler. The user gets a 500 instead of their 302 redirect.

**Fix:** Wrap `recordClick()` in a try/catch in the controller (log and continue), or restore `@Async` and handle the cache eviction differently.

---

### 3. MEDIUM — `clickRepo.save()` failure in `logClickAsync` silently drops the click

**File:** `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java:22`

`@Transactional` was removed from `logClickAsync`. The method now calls `geoResolverService.resolve(ip)` (safe — has a catch-all) then `clickRepo.save(...)` with no try/catch. If `save()` throws, Spring's default `SimpleAsyncUncaughtExceptionHandler` logs at ERROR and swallows the exception. No `AsyncUncaughtExceptionHandler` is configured in `AsyncConfig`. The click is permanently lost with only a log line as evidence.

**Fix:** Add a try/catch around `clickRepo.save()` in `logClickAsync`, or configure a custom `AsyncUncaughtExceptionHandler` that handles the failure (e.g., retry, metrics).

---

### 4. MEDIUM — `topReferrers` / `topUserAgents` silently truncated at 10 with no signal

**File:** `backend/src/main/java/com/avivly/urlshortener/repository/ClickAnalyticsRepository.java:26`

Old JPQL queries returned all results. The new native queries add `LIMIT :limit` (hardcoded as `10` in `AnalyticsService`). `AnalyticsResponse` has no `totalReferrers`/`totalUserAgents` count field. A link with 50 distinct referrers returns only 10 — the client cannot distinguish "exactly 10 referrers" from "truncated at 10". Any analytics export or monitoring that expected complete data is now silently wrong.

**Fix:** Add a total-count field to `AnalyticsResponse`, or document the cap explicitly in the API contract.

---

### 5. MEDIUM — `navigator.clipboard.writeText` rejection is unhandled — no user feedback

**File:** `frontend/src/components/QrPopover.jsx:52`

```jsx
const handleCopy = async () => {
  await navigator.clipboard.writeText(svg);  // can reject
  setCopied(true);
  setTimeout(() => setCopied(false), 2000);
};
```

On HTTP, in a sandboxed iframe, or when clipboard permissions are denied, `writeText()` rejects. With no try/catch, `setCopied(true)` never runs and the button stays at "Copy SVG" with no error message. The test suite only mocks `writeText` as a resolved promise.

**Fix:**
```jsx
const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(svg);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  } catch {
    setError(true);
  }
};
```

---

### 6. PLAUSIBLE — Stale `@Cacheable` result allows concurrent requests to exceed `maxClicks`

**File:** `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java:28`

`findByShortCode` is `@Cacheable` and returns a `ShortLink` with a frozen `totalClicks` value. N concurrent redirect threads can all read the same cached entry where `totalClicks == maxClicks - 1`, all pass `isValid()`, all serve the redirect, then each call `recordClick()` — which atomically increments the DB counter and evicts the cache, but only after all N threads have already passed the validity check. The `@CacheEvict` added in this PR reduces the ongoing window but cannot close the concurrent-read gap.

*Note: this race is pre-existing; the PR did not introduce it, but the addition of `@CacheEvict` makes it worth re-examining.*

---

### 7. PLAUSIBLE — `qrRefs.current` mutated during render, violates React rules

**File:** `frontend/src/components/LinksTable.jsx:61`

```jsx
// Inside render:
if (!qrRefs.current[link.id]) {
  qrRefs.current[link.id] = { current: null };
}
```

Mutating a ref during the render function is a side-effect that violates React's rules. React 18 StrictMode (enabled in `main.jsx`) double-invokes render to surface exactly this pattern. Under concurrent rendering, discarded renders still mutate `qrRefs.current` — a ref that persists across discarded attempts. Deleted links accumulate stale entries indefinitely.

**Fix:** Initialize ref objects inside a `useEffect`, or extract each row into a child component that owns its own `useRef`.

---

### 8. LOW — `CHARS` alphabet constant duplicated across `HashTruncateStrategy`, `SequentialStrategy`, and `Base62.java`

**File:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/HashTruncateStrategy.java:11`

All three declare the same `String CHARS = "abc...0-9"`. If the alphabet is updated in one place (e.g., removing ambiguous chars `0/O/l/1`), the other copies diverge silently. Hash-generated codes and random/sequential codes would use different character sets.

**Fix:** Reference `Base62.CHARS` (or an equivalent constant) from all strategies.

---

### 9. LOW — `ALLOWED_ALGORITHMS` in `StrategyParamValidator` is disconnected from `HashTruncateStrategy`'s schema

**File:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyParamValidator.java:13`

```java
private static final Set<String> ALLOWED_ALGORITHMS = Set.of("SHA-256", "SHA-512");
```

`HashTruncateStrategy.SCHEMA` independently documents the same two values. If a new algorithm is added to the strategy, the developer must remember to update this disconnected set in an unrelated class. Forgetting causes valid requests to be rejected with a 400.

**Fix:** Move the allowed-values list onto `StrategyParamDefinition` (e.g., a `Set<String> allowedValues` field), so the strategy definition is the single source of truth.

---

### 10. LOW — `StrategyRegistry.validateAndGenerate` silently falls back to `RANDOM_BASE62` for unregistered types

**File:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyRegistry.java:21`

```java
CodeGenerationStrategy strategy = strategies.getOrDefault(
    type, strategies.get(StrategyType.RANDOM_BASE62));
```

A new `StrategyType` enum value that is not registered in the constructor silently produces a `RANDOM_BASE62` code. There is no startup failure, no runtime warning, and the `strategy` field on the saved `ShortLink` is correct while the actual encoding is wrong — a silent data integrity bug.

**Fix:** Replace `getOrDefault` with `get` and throw `IllegalStateException` on a missing type.
