# Agent Prompt: Update LinkControllerIntegrationTest to Authenticate (AUTH-15)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-14 are done.
File to modify: `backend/src/test/java/com/avivly/urlshortener/LinkControllerIntegrationTest.java`.

All existing tests in this file call `POST /api/links`, `PUT /api/links/{id}`, and `DELETE /api/links/{id}`
without a Bearer token. Spring Security now returns 401 for those routes, so the tests fail.

The API now returns `LinkResponse` (not `ShortLink`) from all link endpoints. Some test assertions
reference `ShortLink`-specific getters — these must be updated too.

## Your Task
Update `LinkControllerIntegrationTest.java` to authenticate all mutating requests and use `LinkResponse`.

## Changes

### 1. Add imports
```java
import com.avivly.urlshortener.dto.AuthRequest;
import com.avivly.urlshortener.dto.AuthResponse;
import com.avivly.urlshortener.dto.LinkResponse;
import java.util.UUID;
```

### 2. Add shared helpers (add as private methods)
```java
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
```

### 3. Add `@BeforeEach` setup
```java
private String token;

@BeforeEach
void setUp() {
    token = registerAndGetToken("test-" + UUID.randomUUID() + "@example.com");
}
```

### 4. Update all mutating calls
For every `restTemplate.postForEntity(url("/api/links"), req, ShortLink.class)` call:
- Wrap the request in `new HttpEntity<>(req, bearerHeaders(token))`
- Change the response type from `ShortLink.class` to `LinkResponse.class`

For every `PUT /api/links/{id}` call:
- Include bearer headers in the `HttpEntity`

For every `DELETE /api/links/{id}` call:
- Pass `new HttpEntity<>(bearerHeaders(token))` as the entity

### 5. Update assertions that used `ShortLink` getters
- `body.getOriginalUrl()` → `body.originalUrl()`
- `body.getShortCode()` → `body.shortCode()`
- `body.getId()` → `body.id()`
- Change `ParameterizedTypeReference<List<ShortLink>>` → `ParameterizedTypeReference<List<LinkResponse>>`
- Change `.extracting(ShortLink::getOriginalUrl)` → `.extracting(LinkResponse::originalUrl)`

### 6. Analytics test
The analytics test accesses `created.getShortCode()` — update to `created.shortCode()`.

## Acceptance Criteria
- `mvn test -f backend/pom.xml` — all tests in `LinkControllerIntegrationTest` pass
- No references to `ShortLink` remain in this test file (unless needed for model-level tests)
