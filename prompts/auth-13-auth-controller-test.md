# Agent Prompt: Write AuthControllerIntegrationTest (AUTH-13)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-11 are done and the suite compiles.
Test pattern used in this project: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`,
`TestRestTemplate`, AssertJ assertions.
Existing test for reference: `backend/src/test/java/com/avivly/urlshortener/LinkControllerIntegrationTest.java`.

## Your Task
Create a new integration test file.

## File to Create

### `backend/src/test/java/com/avivly/urlshortener/AuthControllerIntegrationTest.java`

```java
package com.avivly.urlshortener;

import com.avivly.urlshortener.dto.AuthRequest;
import com.avivly.urlshortener.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void register_withValidData_returns201AndToken() {
        AuthRequest req = new AuthRequest("alice@example.com", "password123");
        ResponseEntity<AuthResponse> res = restTemplate.postForEntity(
            url("/api/auth/register"), req, AuthResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().token()).isNotBlank();
        assertThat(res.getBody().email()).isEqualTo("alice@example.com");
    }

    @Test
    void register_duplicateEmail_returns409() {
        AuthRequest req = new AuthRequest("dup@example.com", "password123");
        restTemplate.postForEntity(url("/api/auth/register"), req, AuthResponse.class);
        ResponseEntity<String> second = restTemplate.postForEntity(
            url("/api/auth/register"), req, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void login_withCorrectCredentials_returns200AndToken() {
        AuthRequest req = new AuthRequest("bob@example.com", "password123");
        restTemplate.postForEntity(url("/api/auth/register"), req, AuthResponse.class);

        ResponseEntity<AuthResponse> res = restTemplate.postForEntity(
            url("/api/auth/login"), req, AuthResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().token()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returns401() {
        AuthRequest reg = new AuthRequest("carol@example.com", "password123");
        restTemplate.postForEntity(url("/api/auth/register"), reg, AuthResponse.class);

        AuthRequest bad = new AuthRequest("carol@example.com", "wrongpass");
        ResponseEntity<String> res = restTemplate.postForEntity(
            url("/api/auth/login"), bad, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withUnknownEmail_returns401() {
        AuthRequest req = new AuthRequest("ghost@example.com", "password123");
        ResponseEntity<String> res = restTemplate.postForEntity(
            url("/api/auth/login"), req, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

## Acceptance Criteria
- `mvn test -f backend/pom.xml` — all 5 new tests pass
- No side-effects between tests (each uses a unique email)
