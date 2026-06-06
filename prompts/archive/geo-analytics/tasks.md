# Implementation Tasks — IP Geolocation Analytics

**Source:** `tdd-geo-analytics.md` · **PRD:** `prd-geo-analytics.md`
**Module:** Avivly URL Shortener (Spring Boot backend + React frontend)

Tasks are ordered by dependency. Each is atomic, independently committable, and
states its own verification step. Follow the project's existing conventions
(Lombok, records for DTOs, native queries, `recharts` on the frontend).

**Working agreement**
- Run `./mvnw test` (or `mvn test`) after every backend task that adds code.
- Write the test first where a "Test" bullet is listed (TDD); see it fail, then
  implement until it passes.
- Keep each task to one logical commit. Do not push unless asked.
- No raw IP address may ever reach a log statement — only masked output.

---

## Phase 0 — Dependencies & Build Setup

### Task 0.1 — Add Maven dependencies
- Add to `pom.xml`: `com.maxmind.geoip2:geoip2:4.2.0`, `org.flywaydb:flyway-core`,
  `org.flywaydb:flyway-database-postgresql`, `org.springframework.boot:spring-boot-starter-actuator`.
- **Verify:** `mvn dependency:resolve` succeeds; project compiles (`mvn compile`).
- **Blocked by OQ-05** (actuator security review) — confirm before merging, but
  code can proceed.

### Task 0.2 — Add the MaxMind test fixture
- Add `src/test/resources/GeoLite2-City-Test.mmdb` (MaxMind's Apache-2.0 test DB).
- Update `.gitignore`: add `*.mmdb` and the carve-out `!src/test/resources/GeoLite2-City-Test.mmdb`.
- **Verify:** `git status` shows the fixture is tracked and other `*.mmdb` are ignored.

---

## Phase 1 — Data Model & Migration

### Task 1.1 — Create `GeoStatus` enum
- New file `model/GeoStatus.java` with: `PENDING, RESOLVED, PRIVATE, NOT_FOUND, ERROR`.
- **Verify:** compiles.

### Task 1.2 — Add geo fields to `ClickAnalytics`
- New fields in `model/ClickAnalytics.java`:
  - `geoStatus` (`@Enumerated(STRING)`, `@Column(nullable=false, length=20)`, `@Builder.Default = PENDING`)
  - `country` (`VARCHAR(100)`), `city` (`VARCHAR(100)`).
- **Verify:** compiles; existing entity tests still pass.

### Task 1.3 — Flyway baseline + geo migration
- Create `src/main/resources/db/migration/V1__baseline.sql` (snapshot of current schema —
  generate once from existing DB; see **OQ-04**).
- Create `V2__geo_analytics.sql`: `ALTER TABLE` adding `country`, `city`, `geo_status`
  (default `'PENDING'`), plus three indexes (`short_code`; `short_code, country`;
  `short_code, city`).
- **Verify:** app starts against a clean DB and Flyway applies both migrations without error.

---

## Phase 2 — Geo Resolution Core

### Task 2.1 — `GeoResult` record
- New file `dto/GeoResult.java` with factory methods `private_()`, `notFound()`,
  `error()`, `resolved(country, city)`.
- **Verify:** compiles.

### Task 2.2 — `GeoConfig` bean
- New file `config/GeoConfig.java`: `@Bean @Nullable DatabaseReader` reading `geo.db.path`.
  Returns `null` (with a warn log) when path is unset, file missing, or open fails.
- Add `geo.db.path: ${GEO_DB_PATH:}` to `application.yml`.
- **Verify:** app starts both with a valid path (reader non-null) and with no path
  (reader null, warn logged, no crash).

### Task 2.3 — `GeoResolverService` (TDD)
- **Test first** — `GeoResolverServiceTest` using the test `.mmdb` via
  `@TestPropertySource(properties = "geo.db.path=src/test/resources/GeoLite2-City-Test.mmdb")`:
  - public test IP (e.g. `81.2.69.142`) → `RESOLVED` with non-null country
  - `10.0.0.1` and `127.0.0.1` → `PRIVATE` (reader not consulted)
  - unknown public IP → `NOT_FOUND`
  - `reader == null` → `ERROR`
  - `mask()` never emits the raw last octet (IPv4) / last group (IPv6)
- **Then implement** `service/GeoResolverService.java`: `resolve(ip)` with private/loopback
  check, MaxMind lookup, `AddressNotFoundException` → `NOT_FOUND`, other exceptions → `ERROR`,
  and `mask()` for all logging.
- **Verify:** all `GeoResolverServiceTest` cases pass.

### Task 2.4 — `GeoResolverHealthIndicator`
- New file `config/GeoResolverHealthIndicator.java`: `DOWN` (with reason detail) when
  reader is null, else `UP`.
- Add actuator config to `application.yml` (expose `health`, `show-details: always`).
- **Verify:** `/actuator/health` shows the geoResolver component `UP` with fixture present,
  `DOWN` without.

---

## Phase 3 — Wiring Into the Request Path

### Task 3.1 — Real client IP extraction in `RedirectController` (TDD)
- **Test first** — `RedirectController` tests:
  - `X-Real-IP` header wins when present
  - multi-entry `X-Forwarded-For` parsed right-to-left, first non-private entry wins
  - falls back to `remoteAddr` when no headers
- **Then implement** `extractClientIp(request)` (X-Real-IP → XFF right-to-left → remoteAddr)
  and pass the result to `analyticsService.logClickAsync()`. Redirect must still issue 302
  immediately (resolution stays async).
- **Verify:** controller tests pass; redirect latency unchanged.

### Task 3.2 — Populate geo fields in `AnalyticsService.logClickAsync`
- Inject `GeoResolverService`; call `resolve(ip)` and set `country`, `city`, `geoStatus`
  on the saved `ClickAnalytics`. Keep `@Async("analyticsTaskExecutor")`.
- **Verify:** a click from the public test IP persists `geo_status = RESOLVED` and a country.

### Task 3.3 — Aggregate queries in `ClickAnalyticsRepository`
- Add native queries `topCountries(shortCode, limit)` and `topCities(shortCode, limit)`
  (GROUP BY + ORDER BY count DESC + LIMIT; exclude null country/city).
- **Verify:** a `@DataJpaTest` returns correct ordered aggregates.

### Task 3.4 — Extend `AnalyticsResponse` DTO
- Add inner records `CountryCount(country, clicks)` and `CityCount(city, country, clicks)`
  and the `topCountries` / `topCities` list fields.
- **Verify:** compiles; empty lists serialize as `[]` (not null).

### Task 3.5 — Populate DTO in `AnalyticsService.getAnalytics`
- Map repository results into the new DTO fields (top 10 each).
- **Verify:** `GET /api/links/{shortCode}/analytics` returns `topCountries` and `topCities` arrays.

---

## Phase 4 — Frontend

### Task 4.1 — Add Top Countries / Top Cities charts to `AnalyticsPanel.jsx`
- Two horizontal `BarChart`s following the existing pattern (countries `#3b82f6`,
  cities `#10b981` with `"city, country"` label). Add the empty-state placeholder
  ("Geographic data not yet available for this link.") when both arrays are empty.
- No new dependencies (`recharts` already present).
- **Verify:** charts render with data; placeholder shows when both arrays empty.

---

## Phase 5 — Config, Local Dev & Integration

### Task 5.1 — `application-dev.yml` for zero-setup local dev
- New file pointing `geo.db.path` at `src/test/resources/GeoLite2-City-Test.mmdb`.
- **Verify:** running the `dev` profile yields `RESOLVED` rows for the fixture IPs and
  health `UP` without any download.

### Task 5.2 — Switch to Flyway-managed schema
- Set `spring.jpa.hibernate.ddl-auto: validate` and add `spring.flyway.baseline-on-migrate=true`,
  `spring.flyway.baseline-version=1` in `application.yml`.
- **Blocked by Task 1.3 / OQ-04** (baseline must exist first).
- **Verify:** app starts cleanly against an existing DB with `validate`; no schema drift errors.

### Task 5.3 — Integration test suite
- Add/extend integration tests:
  - click from public test IP → `country` non-null, `geo_status = RESOLVED`
  - click from `127.0.0.1` → `PRIVATE`, no country/city
  - missing `.mmdb` on startup → app starts, health `DOWN`, redirect still works
  - analytics endpoint returns populated `topCountries` / `topCities`
  - empty-state → both arrays `[]`
- If H2 PostgreSQL-compat mode rejects the native queries, switch those tests to
  Testcontainers (PostgreSQL).
- **Verify:** full suite green (`mvn verify`).

---

## Phase 6 — CI/CD & Docs (separate ownership)

### Task 6.1 — CI: download GeoLite2 for integration tests
- Add a CI step using `MAXMIND_LICENSE_KEY` secret to fetch the production DB before
  integration tests (per TDD §7.3).

### Task 6.2 — Docker volume + env
- Document/configure mounting `GeoLite2-City.mmdb` and setting `GEO_DB_PATH=/data/GeoLite2-City.mmdb`.

### Task 6.3 — MaxMind attribution
- Add MaxMind attribution to the app's About/legal page (EULA requirement).

---

## Deferred / Out of Scope (do NOT implement now)

- **Privacy gate (TDD §9):** raw `ipAddress` storage decision (drop / truncate /
  retention) is deferred and needs a product decision + follow-up migration.

## Open Questions to resolve before/while implementing

| # | Question | Owner | Blocks |
|---|----------|-------|--------|
| OQ-01 | nginx forwards `X-Forwarded-For` / `X-Real-IP`? | DevOps | E2E prod testing |
| OQ-02 | "Show all" beyond top-10? | Product | Final UI (Task 4.1) |
| OQ-03 | `.mmdb` hot-reload vs restart? | Engineering | Nice-to-have |
| OQ-04 | `ddl-auto: validate` + baseline export | Engineering | Task 1.3 / 5.2 |
| OQ-05 | Actuator endpoint security review | Engineering | Task 0.1 / 2.4 |
