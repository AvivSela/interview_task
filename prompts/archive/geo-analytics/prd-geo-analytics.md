# Product Requirements Document

**Feature:** IP Geolocation Analytics for Shortened Link Clicks
**Project:** Avivly URL Shortener
**Author:** Aviv
**Date:** 2026-06-03
**Status:** Draft
**Source BRD:** `brd-geo-analytics.md` (revised 2026-06-03, see also `brd-geo-analytics-review.md`)

---

## 1. Overview

Link owners currently cannot tell where their audience is coming from. This feature adds Country and City breakdowns to the analytics dashboard by resolving each click's IP address to a geographic location at click time using the MaxMind GeoLite2 database — entirely in-process, with no external API calls and no impact on redirect latency.

---

## 2. Goals

| Goal | Metric |
|------|--------|
| Give link owners geographic insight into their audience | ≥ 90% of public-IP clicks resolve to `RESOLVED` status |
| Keep redirect latency unaffected | < 5 ms additional p95 latency attributable to this feature |
| Maintain analytics pipeline reliability | 0% click records dropped due to a geo failure |
| Stay within MaxMind GeoLite2 EULA | Attribution present; `.mmdb` not committed to VCS; refreshed within 30 days of each MaxMind release |

---

## 3. Non-Goals

- Precise street-level or postal-code geolocation
- Real-time map visualizations (heat maps, pin maps)
- Geolocation-based access control or link blocking
- ISP / ASN enrichment
- Mobile device GPS-based locale detection
- Historical backfill for existing click records
- Any external HTTP-based geo API

---

## 4. User Stories

### 4.1 Link Owner — Dashboard Consumer

| ID | Story | Acceptance Criteria |
|----|-------|---------------------|
| US-01 | As a link owner, I want to see which countries my link clicks are coming from, so I can measure whether my campaign is reaching its intended market. | `topCountries` bar chart renders on the analytics panel with country names and click counts. |
| US-02 | As a link owner, I want to see which cities are driving the most clicks, so I can identify unexpected regional traffic patterns. | `topCities` bar chart renders with `"City, Country"` labels and click counts. |
| US-03 | As a link owner, when no geographic data is available yet for a link, I want a clear message instead of empty charts, so I understand the feature is working but waiting for clicks. | Placeholder text *"Geographic data not yet available for this link."* shown when all geo fields are null. |
| US-04 | As a link owner, I want my analytics to be available instantly after a click without any noticeable delay in the redirect, so the user experience of my shortened links is unaffected. | 302 redirect fires before or concurrently with the geo lookup; user is never blocked. |

### 4.2 Platform Operator

| ID | Story | Acceptance Criteria |
|----|-------|---------------------|
| US-05 | As an operator, I want the application to start and serve redirects even if the GeoLite2 database file is missing or corrupt, so a misconfigured volume mount does not take down link resolution. | Application starts in degraded mode; `GeoResolverHealthIndicator` reports `DOWN`; redirect path remains operational. |
| US-06 | As an operator, I want to know when geo resolution is failing so I can investigate, without raw IP addresses appearing in logs. | `WARN`-level log on lookup failure with masked IP (last octet replaced with `xxx`); no raw IPs in any log output. |
| US-07 | As an operator, I want successful resolutions recorded at DEBUG level so I can trace geo data flow without noise in production logs. | `DEBUG` log line per resolved click: `shortCode={}, ip=x.x.x.xxx, country={}, city={}`. |

---

## 5. Feature Details

### 5.1 Click-Time IP Resolution

**What happens:** When a click is recorded, the system extracts the originating client IP and resolves it to Country + City asynchronously using the GeoLite2-City `.mmdb` file. The result is written back to the click record.

**IP extraction priority:**
1. `X-Real-IP` header (nginx-set, not client-forgeable)
2. `X-Forwarded-For` — parsed right-to-left; first non-private entry wins (left-hand entries are untrusted)
3. `request.getRemoteAddr()` fallback

**Private/loopback IPs** (RFC 1918 ranges, `127.x.x.x`, `::1`) are not sent to GeoLite2 — they produce `geo_status = PRIVATE` with null geo fields.

**Resolution outcomes:**

| Status | Meaning |
|--------|---------|
| `RESOLVED` | Country (and optionally city) successfully looked up |
| `PRIVATE` | IP is private/loopback; no lookup attempted |
| `NOT_FOUND` | Public IP absent from the GeoLite2 database |
| `ERROR` | Lookup threw an exception |
| `PENDING` | Pre-feature record (legacy rows only) |

**Async execution:** Resolution runs in the existing `analytics-*` thread pool (4–10 threads). The 302 redirect is never delayed.

### 5.2 Data Model Changes

Three new fields on `ClickAnalytics`:

| Field | Type | Nullable | Default |
|-------|------|----------|---------|
| `country` | `VARCHAR(100)` | Yes | `NULL` |
| `city` | `VARCHAR(100)` | Yes | `NULL` |
| `geo_status` | `VARCHAR(20)` | No | `PENDING` |

Existing rows keep `NULL` geo fields and `PENDING` status — distinguishable from post-feature failures.

New database indexes (via Flyway migration):

| Index | Columns |
|-------|---------|
| `idx_click_analytics_short_code` | `short_code` |
| `idx_click_analytics_short_code_country` | `(short_code, country)` |
| `idx_click_analytics_short_code_city` | `(short_code, city)` |

### 5.3 Analytics API

`GET /api/links/{shortCode}/analytics` gains two new fields:

```json
{
  "topCountries": [
    { "country": "United States", "clicks": 98 },
    { "country": "Germany",       "clicks": 21 }
  ],
  "topCities": [
    { "city": "Mountain View", "country": "United States", "clicks": 45 },
    { "city": "Berlin",        "country": "Germany",       "clicks": 18 }
  ]
}
```

- Both fields default to `[]` when no geo data exists.
- Top-N limit: **10** (consistent with `topReferrers` / `topUserAgents`).
- Fully backward-compatible — existing fields unchanged.

### 5.4 Dashboard

Two new horizontal bar charts added to `AnalyticsPanel`:

- **Top Countries** — country on Y-axis, click count on X-axis (reuses existing Recharts `BarChart` pattern).
- **Top Cities** — label format `"City, Country"` on Y-axis, click count on X-axis.
- Both sections hidden / replaced by placeholder when no geo data is present.

### 5.5 GeoLite2 Database Lifecycle

| Concern | Decision |
|---------|----------|
| VCS storage | **Not committed.** ~70 MB; EULA redistribution risk. |
| Production delivery | Docker volume mount (enables hot-swap without image rebuild). |
| CI delivery | Download at build time using a MaxMind license key stored as a CI secret. |
| Update cadence | Refreshed within **30 days** of each MaxMind release (EULA obligation). |
| Singleton | `DatabaseReader` instantiated once as a Spring `@Bean`; shared across all threads. |
| Attribution | MaxMind attribution on the application's About / legal page: *"This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com."* |

---

## 6. Privacy Gate (Go / No-Go Before Production)

Before this feature ships to production, the team must decide on the **raw IP retention policy** for `ClickAnalytics.ipAddress`. If EU traffic is in scope, GDPR applies. Options:

| Option | Description |
|--------|-------------|
| A — Drop raw IP post-resolution | Once `country` and `city` are derived, set `ipAddress = NULL`. |
| B — Truncate to /24 prefix | Store only `x.x.x.0` — retains rough geographic signal, not a personal identifier. |
| C — Define retention window | Keep raw IP for N days, then run scheduled anonymization. |

**This is a go/no-go gate, not an open question.** A decision must be recorded before the feature is deployed to a production environment that handles EU traffic.

---

## 7. Open Questions

| # | Question | Owner | Target |
|---|----------|-------|--------|
| OQ-02 | Who owns the nginx config change to forward `X-Forwarded-For` and `X-Real-IP`? This is a prerequisite for real IP extraction in production end-to-end testing. | DevOps | Before FR-01 can be tested end-to-end |
| OQ-04 | Does the dashboard need a "Show all" expansion control for countries/cities beyond the top 10? | Product | Before UI design for FR-06 |

---

## 8. Acceptance Criteria (Summary)

| ID | Criterion |
|----|-----------|
| AC-01 | A click from a public IP → `country` non-null, `city` non-null (if in DB), `geo_status = RESOLVED`. |
| AC-02 | A click from a private/loopback IP → `country IS NULL`, `city IS NULL`, `geo_status = PRIVATE`. No error logged above `DEBUG`. |
| AC-03 | 302 redirect issued before or concurrently with geo lookup — user never blocked. |
| AC-04 | `GET /api/links/{shortCode}/analytics` returns `topCountries` and `topCities` arrays reflecting actual click distribution. |
| AC-05 | Analytics dashboard renders country and city bar charts for geo-enriched links. |
| AC-06 | GeoLite2 lookup exception → click record persisted with null geo fields, `geo_status = ERROR` or `NOT_FOUND`, no user-facing error. |
| AC-07 | No raw (unmasked) IP addresses in any application log output. |
| AC-08 | Missing or corrupt `.mmdb` → application starts in degraded mode (`GeoResolverHealthIndicator = DOWN`); redirect path operational. |
| AC-09 | MaxMind attribution text present per GeoLite2 EULA. |
| AC-10 | `geo_status` distribution across test clicks correctly reflects `RESOLVED`, `PRIVATE`, `NOT_FOUND`, and `ERROR` for corresponding IP inputs. |

---

## 9. Affected Components

| Layer | Component | Change |
|-------|-----------|--------|
| Backend | `RedirectController` | Extract real client IP via `X-Real-IP` / right-to-left `X-Forwarded-For` |
| Backend | `AnalyticsService.logClickAsync()` | Call `GeoResolverService`; populate geo fields before save |
| Backend | `ClickAnalytics` | Add `country`, `city`, `geo_status` columns |
| Backend | `ClickAnalyticsRepository` | Add `topCountries()` and `topCities()` aggregate queries |
| Backend | Flyway migration | Create three analytics indexes |
| Backend | `AnalyticsResponse` (DTO) | Add `topCountries` and `topCities` list fields |
| Backend | `AnalyticsService.getAnalytics()` | Populate new DTO fields |
| Backend | `GeoResolverService` (new) | Singleton wrapping `DatabaseReader`; returns `null` in degraded mode |
| Backend | `GeoResolverHealthIndicator` (new) | Reports `DOWN`/degraded when `.mmdb` unavailable |
| Backend | `GeoConfig` (new) | Spring `@Bean` loading `.mmdb`; degraded-mode startup |
| Backend | `pom.xml` | Add `com.maxmind.geoip2:geoip2` dependency |
| Backend | `src/test/resources/` | Add MaxMind test `.mmdb` fixtures |
| Frontend | `AnalyticsPanel.jsx` | Render two new bar charts from `topCountries` / `topCities` |
| Infra | `nginx.conf` | Forward `X-Forwarded-For` and `X-Real-IP` headers |

---

*End of Document*
