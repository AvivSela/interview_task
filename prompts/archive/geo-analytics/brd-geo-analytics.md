# Business Requirements Document

**Feature:** IP Geolocation Analytics for Shortened Link Clicks
**Project:** Avivly URL Shortener
**Author:** Aviv
**Date:** 2026-06-02
**Status:** Draft (revised 2026-06-03 — see `brd-geo-analytics-review.md`)

---

## 1. Executive Summary

When a user clicks a shortened link, the system already captures their IP address and stores it in `ClickAnalytics`. This feature enriches that data in real time by resolving each IP to a human-readable Country and City using the **MaxMind GeoLite2 local binary database**, then surfaces the geographic breakdown in the analytics dashboard so link owners can understand where their audience comes from.

---

## 2. Problem Statement

The current analytics dashboard shows *when* links are clicked (daily time-series), *from where on the web* (top referrers), and *with what software* (top user-agents). It cannot answer:

- Which countries are driving the most clicks?
- Is a campaign reaching its intended geographic market?
- Are there unexpected traffic spikes from specific regions?

Raw IP addresses are stored today (`ClickAnalytics.ipAddress`) but are never interpreted, making them analytically useless to non-technical users.

---

## 3. Scope

### 3.1 In Scope

| # | Requirement |
|---|-------------|
| BR-01 | Resolve the originating client IP (not proxy IP) for every click event |
| BR-02 | Translate each IP to Country and City at click time using MaxMind GeoLite2 |
| BR-03 | Persist Country, City, and resolution status alongside the existing click record |
| BR-04 | Aggregate geo data per short link (top countries, top cities) |
| BR-05 | Expose geo aggregates through the existing analytics API |
| BR-06 | Display Country and City breakdowns in the analytics dashboard |
| BR-07 | Handle IP resolution failures gracefully (unresolvable IPs, private ranges) |

### 3.2 Out of Scope

- Precise street-level or postal-code geolocation
- Real-time map visualizations (heat maps, pin maps)
- Per-user geolocation history or user identity tracking
- ISP / ASN enrichment
- Geolocation-based access control or link blocking
- Mobile device locale detection (GPS-based)
- Historical backfill of geo data for existing click records
- Any external HTTP-based geo API integration

---

## 4. Functional Requirements

### FR-01 — Real Client IP Extraction

**Current state:** `RedirectController` calls `request.getRemoteAddr()`, which returns the Docker/nginx proxy IP behind a reverse proxy, not the browser's IP.

**Requirement:** Before geolocation lookup, the system must extract the originating client IP using the following header priority order:

1. `X-Real-IP` — nginx sets this to `$remote_addr` (the IP it received directly), which the client cannot forge.
2. `X-Forwarded-For` — parse **right-to-left**, skipping only the rightmost proxy-appended hop(s) you control; take the first non-private entry found going right-to-left. Do **not** trust the leftmost entry unconditionally — clients can pre-set arbitrary values to the left, poisoning the geo distribution with a fake country.
3. `request.getRemoteAddr()` as fallback.

> **IPv6 note:** `X-Forwarded-For` entries may use bracketed notation (e.g., `[2001:db8::1]:port`); the parser must strip brackets and optional port suffixes before validation.

Private/reserved IP ranges must not be sent to the GeoLite2 resolver — they must be treated as unresolvable and result in `null` geo fields and `geo_status = PRIVATE`:

| Range | Description |
|-------|-------------|
| `10.0.0.0/8` | RFC 1918 private |
| `172.16.0.0/12` | RFC 1918 private |
| `192.168.0.0/16` | RFC 1918 private |
| `127.0.0.0/8` | IPv4 loopback |
| `::1` | IPv6 loopback |

### FR-02 — IP-to-Geo Resolution via MaxMind GeoLite2

**Requirement:** For every valid (public) client IP, the system must resolve it to:

- **Country** — Full English name (e.g., `United States`)
- **City** — City name (e.g., `Mountain View`)

Both fields are optional results: if only the country is resolvable, city may be `null`. If neither is resolvable (e.g., the IP is not in the GeoLite2 database), both are `null` and `geo_status = NOT_FOUND`.

**Resolution method: MaxMind GeoLite2 City database (local binary `.mmdb` file)**

The GeoLite2-City database is downloaded at build/deploy time (see FR-08). Lookups are performed entirely in-process using the official [MaxMind GeoIP2 Java client library](https://github.com/maxmind/GeoIP2-java). No network call is made at lookup time.

| Property | Detail |
|----------|--------|
| Database file | `GeoLite2-City.mmdb` (downloaded at CI build time or mounted as a Docker volume — **not committed to git**) |
| Lookup latency | typical ~1 ms (in-memory read, no I/O); ceiling ≤ 5 ms per NFR-02 |
| Library | `com.maxmind.geoip2:geoip2` (Maven dependency) |
| License | MaxMind GeoLite2 EULA — requires a free MaxMind account, attribution, and timely updates (see FR-08) |
| Update cadence | Must be refreshed within 30 days of each MaxMind release (EULA requirement) |
| IPv6 support | GeoLite2-City supports IPv6 natively |

**Resolution must be asynchronous** — it must never block the 302 redirect response. The existing `@Async` analytics thread pool (`analytics-*`, 4–10 threads) is the correct execution context.

**Optional optimisation:** a small in-process IP→geo cache (e.g. Guava `LoadingCache`) can eliminate redundant lookups when many clicks share an IP. Not required for initial delivery, but a natural fit for the async path.

### FR-03 — Data Persistence

**Requirement:** The `ClickAnalytics` entity must be extended with three nullable/constrained fields:

| Field | Type | Constraint | Example |
|-------|------|------------|---------|
| `country` | `VARCHAR(100)` | nullable | `United States` |
| `city` | `VARCHAR(100)` | nullable | `Mountain View` |
| `geo_status` | `VARCHAR(20)` | not null, default `PENDING` | `RESOLVED` / `PRIVATE` / `NOT_FOUND` / `ERROR` |

`geo_status` values:
- `RESOLVED` — country (and optionally city) were successfully looked up
- `PRIVATE` — IP was in a private/loopback range; no lookup attempted
- `NOT_FOUND` — IP is public but absent from the GeoLite2 database
- `ERROR` — lookup threw an exception

Existing rows (pre-feature) retain `NULL` in geo fields and `PENDING` in `geo_status`, which distinguishes pre-feature records from post-feature failures when computing success metrics.

### FR-04 — Analytics Aggregation Queries

**Requirement:** Two new aggregation queries must be added to `ClickAnalyticsRepository`:

1. **Top Countries** — for a given `shortCode`, return a ranked list of `(country, clickCount)` ordered by count descending then `country ASC` (tiebreaker), excluding `NULL` country rows.
2. **Top Cities** — for a given `shortCode`, return a ranked list of `(city, country, clickCount)` ordered by count descending then `city ASC` (tiebreaker), excluding `NULL` city rows.

A configurable limit (default: top 10) must be applied to both queries, consistent with the limit applied to `topReferrers` and `topUserAgents` (align all four aggregations).

**Indexes:** a Flyway migration (not `ddl-auto: update`, which is unreliable for index DDL) must create:

| Index | Columns | Purpose |
|-------|---------|---------|
| `idx_click_analytics_short_code` | `short_code` | Base filter for all analytics queries (currently unindexed) |
| `idx_click_analytics_short_code_country` | `(short_code, country)` | Top-countries aggregation |
| `idx_click_analytics_short_code_city` | `(short_code, city)` | Top-cities aggregation |

### FR-05 — API Response Extension

**Requirement:** The `GET /api/links/{shortCode}/analytics` endpoint response (`AnalyticsResponse`) must include two new fields:

```json
{
  "totalClicks": 142,
  "clicksOverTime": [...],
  "topReferrers": [...],
  "topUserAgents": [...],
  "topCountries": [
    { "country": "United States", "clicks": 98 },
    { "country": "Germany", "clicks": 21 }
  ],
  "topCities": [
    { "city": "Mountain View", "country": "United States", "clicks": 45 },
    { "city": "Berlin", "country": "Germany", "clicks": 18 }
  ]
}
```

Both fields default to empty arrays (`[]`) when no geo data exists for the link. The API must remain fully backward-compatible — existing consumers receive the same fields as before, plus the new ones.

> **Implementation note:** `AnalyticsService` currently builds `AnalyticsResponse` via a positional constructor. Adding these two fields changes every constructor call site — note this as a code change, not a purely additive API change.

### FR-06 — Dashboard Display

**Requirement:** The `AnalyticsPanel` component must display the two new data sections when `topCountries` or `topCities` contain at least one entry:

- **Top Countries** — horizontal bar chart (reusing existing Recharts `BarChart` pattern), country name on Y-axis, click count on X-axis.
- **Top Cities** — same chart pattern, label format `"City, Country"`.

When no geo data is available (all entries have null geo fields), a placeholder message must be shown:
> *"Geographic data not yet available for this link."*

### FR-07 — Failure Handling

**Requirement:** Geolocation resolution failures must not cause analytics loss. The system must:

- Catch all exceptions from the GeoLite2 resolver within the async method
- Set `geo_status = ERROR` on the click record
- Log the failure at `WARN` level with a sanitized IP (last octet masked, e.g., `8.8.8.xxx`)
- Persist the click record with `country = NULL`, `city = NULL` rather than dropping the record
- Not retry failed lookups (fire-and-forget; the data cost of a missed lookup is low)

### FR-08 — GeoLite2 Database Lifecycle

**Requirement:** The `.mmdb` file must be kept up to date and must **not** be committed to git:

- **No VCS storage:** `GeoLite2-City.mmdb` must not be committed to the repository. It is ~70 MB and its redistribution may violate the MaxMind GeoLite2 EULA if the repo is shared.
- **Delivery mechanism:** Prefer a Docker volume mount for production (supports hot-swap without image rebuild). For CI, download the file at build time using a MaxMind license key stored as a CI secret.
- **Update cadence:** The database must be refreshed within **30 days** of each MaxMind release — this is an EULA obligation, not a recommended cadence.
- **Attribution:** MaxMind attribution must appear in any user-visible "About" or legal page: *"This product includes GeoLite2 data created by MaxMind, available from [https://www.maxmind.com](https://www.maxmind.com)."*

---

## 5. Non-Functional Requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-01 | Performance | Geo resolution must not add latency to the redirect response. The 302 must be returned before or concurrently with the lookup. |
| NFR-02 | Performance | GeoLite2 lookup latency: typical ~1 ms, ceiling ≤ 5 ms per call. Geo aggregation queries must return within 200 ms at p95 for links with up to 100,000 click records. |
| NFR-03 | Privacy / Compliance | Raw IP addresses must not appear in application logs. Only masked IPs (last octet redacted) are acceptable in log output. No IP data is transmitted to any external system during geo lookup (GeoLite2 is entirely local). Note: raw IPs are also persisted in `ClickAnalytics.ipAddress` — see C-05 for the retention gate. |
| NFR-04 | Accuracy | Geo resolution accuracy is best-effort. VPN/proxy/Tor exit nodes resolve to their exit location. Accuracy is constrained by the GeoLite2 database itself (~99.8% country-level accuracy per MaxMind documentation). No accuracy SLA is defined. |
| NFR-05 | Availability | If the `.mmdb` file is missing or corrupt at startup, the application must start in **degraded mode**: log an `ERROR`, expose a `GeoResolverHealthIndicator` reporting `DOWN`/degraded, and have `GeoResolverService.resolve()` return `null` geo for all requests. The redirect path must never be blocked by a geo failure. A `strict-geo` Spring profile may opt into fail-fast behaviour for environments that require it. |
| NFR-06 | Resource usage | The GeoLite2-City database is ~70 MB in memory when loaded. The `DatabaseReader` instance must be a singleton (Spring `@Bean`) shared across all threads — never instantiated per-request. |
| NFR-07 | Observability | A `DEBUG`-level log line should record each successful geo resolution: `shortCode={}, ip=x.x.x.xxx, country={}, city={}`. |

---

## 6. Constraints and Assumptions

| # | Statement |
|---|-----------|
| C-01 | The application runs behind an nginx reverse proxy (per `nginx.conf` and `docker-compose.yml`). The proxy must be configured to forward `X-Forwarded-For` and `X-Real-IP` headers. This is an infrastructure prerequisite for FR-01 to function correctly. |
| C-02 | Hibernate `ddl-auto: update` is active and will add the new nullable columns automatically on startup. However, `ddl-auto: update` is unreliable for index DDL — the indexes required by FR-04 must be created via a Flyway migration. |
| C-03 | The existing `ClickAnalytics.ipAddress` field is `VARCHAR(45)` — sufficient for IPv6. The GeoLite2 library supports both IPv4 and IPv6 inputs. |
| C-04 | A free MaxMind account is required to download GeoLite2 databases. A license key must be stored as a secret in the CI/CD pipeline for the build-time download and the monthly update process. |
| C-05 | **Privacy gate (promoted from OQ-03):** Before production deployment, a decision must be made on the raw IP retention policy in `ClickAnalytics.ipAddress`. If EU traffic is in scope, GDPR applies — options include dropping the raw IP once geo is derived, truncating it to a /24 prefix, or defining a retention window with scheduled anonymization. This is a go/no-go gate, not an open question. |
| A-01 | Country and City names are stored in English only (`en` locale). Internationalization is out of scope. |
| A-02 | The feature targets the internal analytics dashboard only. No geo data is exposed to the link-clicker. |
| A-03 | The existing async thread pool (4 core / 10 max / 500 queue) is sufficient for GeoLite2 lookups given their typical ~1 ms latency (≤ 5 ms ceiling per NFR-02). |

---

## 7. Open Questions

| # | Question | Owner | Target Resolution |
|---|----------|-------|-------------------|
| OQ-01 | ~~Should the `.mmdb` file be bundled directly into the Docker image or mounted as a Docker volume?~~ **Resolved:** Do not commit to git (EULA redistribution risk). Prefer volume mount for production (hot-swap without rebuild); use build-time download via CI secret for image builds. | Engineering / DevOps | Resolved |
| OQ-02 | Who owns the nginx config change to forward `X-Forwarded-For` and `X-Real-IP`? This is a prerequisite for real IP extraction in production. | DevOps | Before FR-01 can be tested end-to-end |
| OQ-04 | Top-N limit for countries/cities: default 10 is assumed. Does the dashboard need a "show all" expansion control? | Product | Before FR-06 UI design |

---

## 8. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | Clicking a short link from a public IP results in a `ClickAnalytics` row where `country` and `city` are non-null and `geo_status = RESOLVED` (when the IP is present in the GeoLite2 database). |
| AC-02 | Clicking a short link from a private or loopback IP results in a row where `country IS NULL`, `city IS NULL`, and `geo_status = PRIVATE`, with no error thrown or logged above `DEBUG`. |
| AC-03 | The 302 redirect is issued before or concurrently with the geo lookup — the user is never blocked waiting for geolocation. |
| AC-04 | `GET /api/links/{shortCode}/analytics` returns `topCountries` and `topCities` arrays reflecting the click distribution for that link. |
| AC-05 | The analytics dashboard renders country and city bar charts for a link that has geo-enriched clicks. |
| AC-06 | If the GeoLite2 lookup throws (e.g., IP not in database), the click record is still persisted with null geo fields, `geo_status = ERROR` or `NOT_FOUND`, and no user-facing error occurs. |
| AC-07 | No raw (unmasked) IP addresses appear in application log output. |
| AC-08 | The `DatabaseReader` is instantiated once at application startup and reused across all requests. A missing or corrupt `.mmdb` file causes the application to start in degraded mode (`GeoResolverHealthIndicator = DOWN`, geo resolution returns `null`) rather than failing to start — the redirect path remains operational. |
| AC-09 | MaxMind attribution text is present in the application per GeoLite2 EULA requirements. |
| AC-10 | The `geo_status` distribution for a set of test clicks correctly reflects `RESOLVED`, `PRIVATE`, `NOT_FOUND`, and `ERROR` for the corresponding IP inputs. |

---

## 9. Affected Components

| Layer | Component | Change Type |
|-------|-----------|-------------|
| Backend — Controller | `RedirectController` | Extract real client IP via `X-Real-IP` / right-to-left `X-Forwarded-For` before passing to `AnalyticsService` |
| Backend — Service | `AnalyticsService.logClickAsync()` | Add GeoLite2 resolution call; populate `country`, `city`, and `geo_status` on `ClickAnalytics` before save |
| Backend — Model | `ClickAnalytics` | Add `country VARCHAR(100)`, `city VARCHAR(100)` nullable columns, and `geo_status VARCHAR(20)` not-null column |
| Backend — Repository | `ClickAnalyticsRepository` | Add `topCountries()` and `topCities()` aggregate queries |
| Backend — Migration | `Flyway V{n}__geo_indexes.sql` | Create `idx_click_analytics_short_code`, `_short_code_country`, `_short_code_city` indexes |
| Backend — DTO | `AnalyticsResponse` | Add `topCountries` and `topCities` list fields; **note:** positional constructor call sites change |
| Backend — Service | `AnalyticsService.getAnalytics()` | Populate new DTO fields from new repository queries |
| Backend — New | `GeoResolverService` | Singleton Spring bean wrapping `DatabaseReader`; exposes `resolve(ip) → GeoLocation`; returns `null` in degraded mode |
| Backend — New | `GeoResolverHealthIndicator` | Spring Boot `HealthIndicator` reporting `DOWN`/degraded when `.mmdb` is unavailable |
| Backend — Config | `GeoConfig` | Spring `@Bean` definition loading `GeoLite2-City.mmdb` from volume path or classpath; starts in degraded mode if file is absent |
| Backend — Dependency | `pom.xml` | Add `com.maxmind.geoip2:geoip2` Maven dependency |
| Backend — Resources | `src/test/resources/` | Add MaxMind test `.mmdb` fixtures for unit/integration tests (do **not** add production `.mmdb` to git) |
| Frontend — Component | `AnalyticsPanel.jsx` | Render two new bar charts from `topCountries` / `topCities` response fields |
| Infra | `nginx.conf` | Add `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` and `proxy_set_header X-Real-IP $remote_addr` |

---

## 10. Success Metrics

| Metric | Target |
|--------|--------|
| % of click events with `geo_status = RESOLVED` among public-IP clicks | ≥ 90% (measurable via `geo_status` — `PRIVATE` and pre-feature `PENDING` rows are excluded from the denominator) |
| Redirect p95 latency delta | < 5 ms additional latency attributable to this feature (GeoLite2 lookup is in-process and sub-millisecond) |
| Startup time delta | < 2 s additional startup time for loading the ~70 MB GeoLite2 database into the `DatabaseReader` |

---

*End of Document*
