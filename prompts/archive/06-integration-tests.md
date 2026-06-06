# Agent Prompt: Integration Tests — Redirect Flow (TASK-26)

## Project Context
You are building an **analytics-driven URL shortener**.

The full backend stack is implemented:
- **`LinkService`**: `create(CreateLinkRequest)`, `findByShortCode(String)`
- **`RedirectController`**: `GET /{shortCode}` → 302 on valid link, 410 on invalid/expired/exhausted
- **`ShortLink`** model: `isValid()` checks `isActive`, `expiresAt`, `maxClicks` vs `totalClicks`

The `pom.xml` already includes `h2` as a test-scoped dependency.

## Your Task
Create the integration test class and the test-profile YAML configuration.

## Files to Create

### `backend/src/test/resources/application-test.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
    show-sql: false
  cache:
    type: caffeine
```

### `backend/src/test/java/com/avivly/urlshortener/RedirectIntegrationTest.java`
```java
package com.avivly.urlshortener;

import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RedirectIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LinkService linkService;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void happyPath_redirectsToOriginalUrl() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/happy", null, null, null, null, null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
            .hasToString("https://example.com/happy");
    }

    @Test
    void expiredLink_returns410() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/expired", null, null, null,
            LocalDateTime.now().minusSeconds(1), null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void clickExhausted_secondRequestReturns410() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/limited", null, null, 1, null, null));

        restTemplate.getForEntity(url("/" + link.getShortCode()), Void.class);

        ResponseEntity<Void> second = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void unknownCode_returns410() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/nonexistent999"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }
}
```

## Important Notes
- `TestRestTemplate` does NOT follow redirects by default — so the `302` response is what you receive directly, which is exactly what the tests check.
- The `clickExhausted` test works because `ShortLink.isValid()` checks `totalClicks >= maxClicks`. After the first redirect, `analyticsService.logClickAsync` increments the counter asynchronously. If the async timing causes flakiness, you may need to add a brief `Thread.sleep(200)` between the two calls or configure a synchronous executor for the test profile.
- The `@ActiveProfiles("test")` annotation activates `application-test.yml` which uses H2 in-memory DB.

## Acceptance Criteria
- All 4 tests pass with `mvn test -f backend/pom.xml`
- Tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate`
- H2 in-memory DB used via `application-test.yml` — no real PostgreSQL required for tests
- No mocking of repositories or services — these are true integration tests hitting the full stack
