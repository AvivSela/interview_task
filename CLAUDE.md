# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Analytics-driven URL shortener built with Spring Boot 3 (Java 17), React + Vite + Tailwind CSS, and PostgreSQL. Three services orchestrated by Docker Compose: `db` → `backend` → `frontend` (strict startup order enforced via healthchecks).

## Commands

### Running the full stack
```bash
docker-compose up --build          # build and start all services
docker-compose up --build backend  # rebuild a single service
docker-compose down -v             # stop and wipe the DB volume
```

### Backend (Spring Boot + Maven)
```bash
cd backend
mvn spring-boot:run                # start on :8080 (connects to localhost:5432)
mvn test                           # run all tests
mvn test -Dtest=RedirectIntegrationTest  # run a single test class
mvn clean package -DskipTests      # build the JAR
```

Tests use an in-memory H2 database (`@ActiveProfiles("test")`) — no running Postgres required.

### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev      # dev server on :5173; /api/* proxied to :8080
npm run build    # production build
npx vitest       # run tests
npx vitest run src/components/LinksTable.test.jsx  # run a single test file
```

## Architecture

### Backend package layout (`com.memcyco.urlshortener`)
- `controller/` — `RedirectController` (public redirect at `/{shortCode}` and `/api/r/{shortCode}`), `LinkController` (CRUD at `/api/links`), `StrategyController` (strategy metadata)
- `service/` — `LinkService` (create/update/delete + Caffeine cache), `AnalyticsService` (query analytics; `logClickAsync` is `@Async`), `GeoResolverService` (optional MaxMind GeoIP2 lookup)
- `util/strategy/` — pluggable code generation via `CodeGenerationStrategy` interface; `StrategyRegistry` wires the three built-in strategies (`RANDOM_BASE62`, `HASH_TRUNCATE`, `SEQUENTIAL`)
- `config/` — `CacheConfig` (Caffeine, 10-min TTL, 10K entries), `AsyncConfig` (analytics thread pool: 4 core / 10 max / 500-queue, prefix `analytics-`), `GeoConfig` (optional `DatabaseReader` bean, null if path unset)
- `dto/` — request/response records (`CreateLinkRequest`, `UpdateLinkRequest`, `AnalyticsResponse`, `GeoResult`)
- `model/` — `ShortLink` (entity with `isValid()` gate), `ClickAnalytics`, `GeoStatus` (enum)

### Key design decisions
- **Redirect flow:** lookup → `isValid()` check → `recordClick()` (`@CacheEvict`) → `logClickAsync()` (fire-and-forget) → `302` to original URL. Invalid/unknown codes redirect to `/link-expired` (frontend route), not `410`.
- **Cache eviction on every click:** `totalClicks` lives on the cached `ShortLink` entity; `@CacheEvict` after each `recordClick` ensures max-click limits are enforced on the next lookup.
- **SEQUENTIAL strategy two-phase save:** `saveAndFlush` to get the DB-assigned ID, encode it to Base62, then save again. Conflict on second save → delete partial row, return `409`.
- **Geo resolution is optional:** `GeoConfig` emits a null `DatabaseReader` bean when `geo.db.path` is unset or missing. `GeoResolverService.isEnabled()` gates geo columns in analytics responses. Set `GEO_DB_PATH` env var (or `geo.db.path` in yml) to a MaxMind `GeoLite2-City.mmdb` file path to enable it.
- **Schema managed by Flyway:** migrations live in `backend/src/main/resources/db/migration/`. `ddl-auto: validate` in production (schema must match entities); `create-drop` in tests (H2).
- **IP extraction:** `RedirectController.extractClientIp` reads `X-Real-IP` first, then walks `X-Forwarded-For` rightmost-to-leftmost skipping private addresses. Falls back to `request.getRemoteAddr()`.

### Frontend
Single-page app with one real route (`/`) and one fallback route (`/link-expired`). State lives in `App.jsx`: links list, current edit target, active analytics short code, and tag filter. `api.js` is an axios instance; all calls go through it. `AnalyticsPanel` is rendered inline below the table when a short code is selected — it is not a modal.

### Geo feature
The `geo/` directory at the project root contains documentation and scripts for setting up the MaxMind database. The test suite includes a real (sample) `GeoLite2-City-Test.mmdb` at `backend/src/test/resources/` used by `GeoAnalyticsIntegrationTest` and `GeoResolverServiceTest`. The `application-dev.yml` profile points to this test database for local development with geo enabled.
