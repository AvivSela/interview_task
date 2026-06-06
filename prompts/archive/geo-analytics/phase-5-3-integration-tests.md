# Phase 5.3 — Integration Test Suite

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Test DB: H2 (in-memory, PostgreSQL compat mode). Test fixture: `GeoLite2-City-Test.mmdb`.

**Prerequisites:** All previous phases complete (entire geo pipeline is wired).

## Objective

Add/extend integration tests that cover the full geo-resolution path end-to-end:
click recording, geo persistence, analytics endpoint output, health indicator, and
graceful degradation without a DB file.

## Implementation

### Option A — Extend `RedirectIntegrationTest` (preferred if H2 native queries work)

Check whether `@DataJpaTest` / `@SpringBootTest` with H2 accepts the `nativeQuery = true`
queries from `ClickAnalyticsRepository`. If H2 rejects PostgreSQL-specific syntax,
switch to Testcontainers (Option B).

### Option A — H2 approach

Add `backend/src/test/resources/application-test.properties` (create if absent):
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
spring.jpa.hibernate.ddl-auto=create-drop
```

Create / extend
`backend/src/test/java/com/avivly/urlshortener/GeoAnalyticsIntegrationTest.java`:

```java
package com.avivly.urlshortener;

import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.model.GeoStatus;
import com.avivly.urlshortener.repository.ClickAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "geo.db.path=src/test/resources/GeoLite2-City-Test.mmdb"
})
class GeoAnalyticsIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ClickAnalyticsRepository clickRepo;

    // Assumes a test link with shortCode "test" is seeded in @BeforeEach or test DB.
    // Adjust shortCode setup to match the project's test fixtures.

    @Test
    void publicIpClickPersistsResolvedGeoStatus() {
        rest.getForEntity("/api/r/test", Void.class,
            // pass X-Real-IP header via HttpEntity if needed
        );
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ClickAnalytics> clicks = clickRepo.findByShortCodeOrderByClickedAtDesc("test");
            assertThat(clicks).isNotEmpty();
            assertThat(clicks.get(0).getGeoStatus()).isEqualTo(GeoStatus.RESOLVED);
            assertThat(clicks.get(0).getCountry()).isNotBlank();
        });
    }

    @Test
    void loopbackIpClickPersistsPrivateGeoStatus() {
        rest.getForEntity("/api/r/test", Void.class);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<ClickAnalytics> clicks = clickRepo.findByShortCodeOrderByClickedAtDesc("test");
            assertThat(clicks).isNotEmpty();
            ClickAnalytics last = clicks.get(0);
            // TestRestTemplate uses 127.0.0.1 by default
            assertThat(last.getGeoStatus()).isEqualTo(GeoStatus.PRIVATE);
            assertThat(last.getCountry()).isNull();
            assertThat(last.getCity()).isNull();
        });
    }

    @Test
    void analyticsEndpointReturnsGeoArrays() {
        ResponseEntity<String> res = rest.getForEntity(
            "/api/links/test/analytics", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("topCountries", "topCities");
    }

    @Test
    void analyticsEndpointReturnsEmptyGeoArraysWhenNoData() {
        ResponseEntity<String> res = rest.getForEntity(
            "/api/links/nonexistent-code/analytics", String.class);
        assertThat(res.getBody()).contains("\"topCountries\":[]", "\"topCities\":[]");
    }
}
```

Add Awaitility to `pom.xml` if not present:
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

### Additional test: missing `.mmdb` graceful degradation

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "geo.db.path=")
class GeoDisabledIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void appStartsWithoutMmdbAndRedirectStillWorks() {
        ResponseEntity<Void> res = rest.getForEntity("/api/r/test", Void.class);
        assertThat(res.getStatusCode().value()).isIn(302, 404); // no 500
    }

    @Test
    void healthShowsGeoResolverDown() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health", String.class);
        assertThat(res.getBody()).contains("\"geoResolver\"");
        assertThat(res.getBody()).contains("\"status\":\"DOWN\"");
    }
}
```

### Option B — Switch to Testcontainers (if H2 rejects native queries)

If native queries fail under H2, add Testcontainers:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Annotate the test class with:
```java
@Testcontainers
class GeoAnalyticsIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    // ... same tests
}
```

## Verify

```bash
cd backend && ./mvnw verify
```

Full suite (unit + integration) must be green. No test may be skipped or disabled.

## Commit

`feat: add geo-analytics integration tests (Phase 5.3)`
