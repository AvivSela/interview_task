# Agent Prompt: Write LinkAuthorizationIntegrationTest (AUTH-14)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-11 are done. AUTH-13 (AuthControllerIntegrationTest) is done.
Test pattern: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, `TestRestTemplate`, AssertJ.
`LinkResponse` DTO fields: id, shortCode, originalUrl, strategy, isActive, maxClicks, totalClicks, expiresAt, tags, createdAt, ownerId.

## Your Task
Create a new integration test that verifies authorization rules on all link endpoints.

## File to Create

### `backend/src/test/java/com/avivly/urlshortener/LinkAuthorizationIntegrationTest.java`

```java
package com.avivly.urlshortener;

import com.avivly.urlshortener.dto.AuthRequest;
import com.avivly.urlshortener.dto.AuthResponse;
import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.LinkResponse;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LinkAuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String registerAndGetToken(String email) {
        AuthRequest req = new AuthRequest(email, "password123");
        return restTemplate.postForEntity(url("/api/auth/register"), req, AuthResponse.class)
            .getBody().token();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    void createLink_withoutToken_returns401() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/unauth", null, null, null, null, null, null);
        ResponseEntity<String> res = restTemplate.postForEntity(url("/api/links"), req, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createLink_withValidToken_returns201AndOwnerIdSet() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/auth-create", null, null, null, null, null, null);
        HttpEntity<CreateLinkRequest> entity = new HttpEntity<>(req, bearerHeaders(token));

        ResponseEntity<LinkResponse> res = restTemplate.postForEntity(
            url("/api/links"), entity, LinkResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().ownerId()).isNotNull();
    }

    @Test
    void updateLink_byOwner_returns200() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(token, "https://example.com/owner-update");

        UpdateLinkRequest upd = new UpdateLinkRequest("https://example.com/updated", null, null, null, null);
        HttpEntity<UpdateLinkRequest> entity = new HttpEntity<>(upd, bearerHeaders(token));

        ResponseEntity<LinkResponse> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.PUT, entity, LinkResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateLink_byNonOwner_returns403() {
        String ownerToken = registerAndGetToken(UUID.randomUUID() + "@example.com");
        String otherToken = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(ownerToken, "https://example.com/403-update");

        UpdateLinkRequest upd = new UpdateLinkRequest("https://example.com/hacked", null, null, null, null);
        HttpEntity<UpdateLinkRequest> entity = new HttpEntity<>(upd, bearerHeaders(otherToken));

        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.PUT, entity, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateLink_withoutToken_returns401() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(token, "https://example.com/401-update");

        UpdateLinkRequest upd = new UpdateLinkRequest("https://example.com/x", null, null, null, null);
        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.PUT, new HttpEntity<>(upd), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteLink_byOwner_returns204() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(token, "https://example.com/owner-delete");

        ResponseEntity<Void> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders(token)), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteLink_byNonOwner_returns403() {
        String ownerToken = registerAndGetToken(UUID.randomUUID() + "@example.com");
        String otherToken = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(ownerToken, "https://example.com/403-delete");

        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders(otherToken)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteLink_withoutToken_returns401() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(token, "https://example.com/401-delete");

        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/links/" + created.id()), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getLinks_withoutToken_returns200() {
        ResponseEntity<String> res = restTemplate.getForEntity(url("/api/links"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void redirect_withoutToken_follows302() {
        String token = registerAndGetToken(UUID.randomUUID() + "@example.com");
        LinkResponse created = createLink(token, "https://example.com/redirect-test");

        ResponseEntity<Void> res = restTemplate.getForEntity(
            url("/" + created.shortCode()), Void.class);
        // TestRestTemplate follows redirects by default; just confirm it doesn't 401/403
        assertThat(res.getStatusCode().value()).isNotEqualTo(401);
        assertThat(res.getStatusCode().value()).isNotEqualTo(403);
    }

    private LinkResponse createLink(String token, String originalUrl) {
        CreateLinkRequest req = new CreateLinkRequest(
            originalUrl, null, null, null, null, null, null);
        HttpEntity<CreateLinkRequest> entity = new HttpEntity<>(req, bearerHeaders(token));
        return restTemplate.postForEntity(url("/api/links"), entity, LinkResponse.class).getBody();
    }
}
```

## Acceptance Criteria
- All 10 tests pass (`mvn test -f backend/pom.xml`)
- Each test uses unique emails (via UUID) so tests are independent
