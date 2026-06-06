# Phase 2.1 + 2.2 — Geo Core Setup (parallel)

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Tasks 2.1 and 2.2 are independent — run them with two parallel subagents.

**Prerequisite:** Phase 0 complete (geoip2 on classpath).

---

## Spawn two parallel subagents

### Subagent 1 — Task 2.1: `GeoResult` record

Create file:
`backend/src/main/java/com/avivly/urlshortener/dto/GeoResult.java`

```java
package com.avivly.urlshortener.dto;

import com.avivly.urlshortener.model.GeoStatus;
import org.springframework.lang.Nullable;

public record GeoResult(
        GeoStatus status,
        @Nullable String country,
        @Nullable String city
) {
    public static GeoResult private_() {
        return new GeoResult(GeoStatus.PRIVATE, null, null);
    }

    public static GeoResult notFound() {
        return new GeoResult(GeoStatus.NOT_FOUND, null, null);
    }

    public static GeoResult error() {
        return new GeoResult(GeoStatus.ERROR, null, null);
    }

    public static GeoResult resolved(String country, String city) {
        return new GeoResult(GeoStatus.RESOLVED, country, city);
    }
}
```

**Verify:** `cd backend && ./mvnw compile`

---

### Subagent 2 — Task 2.2: `GeoConfig` bean

**Step 1** — Add config property to `backend/src/main/resources/application.yml`:

```yaml
geo:
  db:
    path: ${GEO_DB_PATH:}
```

**Step 2** — Create file:
`backend/src/main/java/com/avivly/urlshortener/config/GeoConfig.java`

```java
package com.avivly.urlshortener.config;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;

@Configuration
public class GeoConfig {

    private static final Logger log = LoggerFactory.getLogger(GeoConfig.class);

    @Bean
    @Nullable
    public DatabaseReader geoDbReader(@Value("${geo.db.path:}") String path) {
        if (path == null || path.isBlank()) {
            log.warn("geo.db.path is not set — geo resolution disabled");
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            log.warn("MaxMind DB not found at {} — geo resolution disabled", path);
            return null;
        }
        try {
            return new DatabaseReader.Builder(file).withCache(new CHMCache()).build();
        } catch (IOException e) {
            log.warn("Failed to open MaxMind DB at {} — geo resolution disabled: {}", path, e.getMessage());
            return null;
        }
    }
}
```

**Verify:**
- App starts with `GEO_DB_PATH` unset → warn logged, no crash.
- App starts with `GEO_DB_PATH=backend/src/test/resources/GeoLite2-City-Test.mmdb` → no warn.

`cd backend && ./mvnw compile`

---

## After both subagents finish

```bash
cd backend && ./mvnw test
```

Full test suite must stay green.
Commit: `feat: add GeoResult record and GeoConfig bean (Phase 2.1-2.2)`
