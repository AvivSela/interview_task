# Phase 0 — Dependencies & Build Setup (Tasks 0.1 + 0.2)

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Tasks 0.1 and 0.2 are independent — run them with two parallel subagents.

## Objective

Add the MaxMind GeoIP2 Maven dependency and the MaxMind test fixture database
so the geo-resolution feature can be built and tested without a production DB download.

---

## Spawn two parallel subagents

### Subagent 1 — Task 0.1: Add Maven dependencies

**File:** `backend/pom.xml`

Add the following three dependencies inside the `<dependencies>` block:

```xml
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.2.0</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Verify:** run `cd backend && ./mvnw dependency:resolve && ./mvnw compile` — both must succeed.

> Note: Actuator security exposure (OQ-05) is a pre-merge concern; code can proceed.

---

### Subagent 2 — Task 0.2: Add the MaxMind test fixture

1. Download (or copy from the MaxMind test-data repo, Apache-2.0 licensed) the file
   `GeoLite2-City-Test.mmdb` and place it at:
   `backend/src/test/resources/GeoLite2-City-Test.mmdb`

2. Update `backend/.gitignore` (create if absent) — add these two lines:
   ```
   *.mmdb
   !src/test/resources/GeoLite2-City-Test.mmdb
   ```

**Verify:** `git status` shows the fixture is tracked (not ignored) and other `*.mmdb`
files (if any) would be ignored.

---

## After both subagents finish

Run `cd backend && ./mvnw test` — full test suite must stay green.
Commit with message: `feat: add geoip2 dependency and MaxMind test fixture (Phase 0)`
