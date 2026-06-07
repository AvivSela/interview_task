# TDD: Link Authorization — Creator-Only Edit & Delete

> Companion to [`prd-authorization.md`](./prd-authorization.md). This document translates the PRD's requirements into concrete code-level design: entity shapes, class signatures, SQL, API contracts, and a test plan grounded in the existing codebase patterns.

---

## Architecture Overview

### Request flow

```
                          ┌─────────────────────────────┐
                          │   JwtAuthenticationFilter    │
                          │  (OncePerRequestFilter)      │
                          │                              │
Client ──HTTP request──▶  │  1. Read Authorization header│
                          │  2. Validate JWT             │
                          │  3. Set SecurityContext       │
                          └────────────┬────────────────┘
                                       │
                          ┌────────────▼────────────────┐
                          │       SecurityConfig         │
                          │                              │
                          │  Public:                     │
                          │    GET  /api/links           │
                          │    GET  /api/strategies      │
                          │    GET  /api/r/**            │
                          │    GET  /{shortCode}         │
                          │    POST /api/auth/**         │
                          │                              │
                          │  Protected (auth required):  │
                          │    POST   /api/links         │
                          │    PUT    /api/links/**      │
                          │    DELETE /api/links/**      │
                          └────────────┬────────────────┘
                                       │
                          ┌────────────▼────────────────┐
                          │  LinkController / AuthController │
                          │  (extracts userId from       │
                          │   SecurityContextHolder)     │
                          └────────────┬────────────────┘
                                       │
                          ┌────────────▼────────────────┐
                          │  LinkService / AuthService   │
                          │  (ownership checks, BCrypt)  │
                          └─────────────────────────────┘
```

### Token lifecycle

```
POST /api/auth/register or /login
        │
        ▼
  AuthService validates credentials / creates user
        │
        ▼
  JwtTokenProvider.generateToken(user)
  → signed HS256 JWT {sub: userId, email, exp}
        │
        ▼
  { "token": "<jwt>", "email": "..." }  ◀── stored in localStorage
        │
        ▼ (subsequent requests)
  Authorization: Bearer <jwt>
        │
        ▼
  JwtAuthenticationFilter
  → validateToken → getUserIdFromToken → userId set as principal
        │
        ▼
  Controller reads (Long) auth.getPrincipal() → passes to service
```

---

## Data Model

### New table: `users`

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
```

### Modified table: `short_links`

```sql
-- Existing rows purged in migration (see V4 below)
ALTER TABLE short_links
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);

CREATE INDEX idx_short_links_user_id ON short_links(user_id);
```

### `model/User.java` (new)

```java
package com.avivly.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### `model/ShortLink.java` — new field

Add after the existing `tags` field:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User owner;
```

---

## Database Migration

### `db/migration/V4__add_auth.sql`

```sql
-- Purge existing links (no owner to associate them with).
-- Acceptable for a pre-production system. In production: add nullable column,
-- insert a system user, backfill, then add NOT NULL constraint.
DELETE FROM short_links;

-- Users table
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- Add ownership FK to short_links
ALTER TABLE short_links
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);

CREATE INDEX idx_short_links_user_id ON short_links(user_id);
```

---

## New Backend Components

### `repository/UserRepository.java`

```java
package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### `dto/AuthRequest.java`

```java
package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.*;

public record AuthRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}
```

### `dto/AuthResponse.java`

```java
package com.avivly.urlshortener.dto;

public record AuthResponse(String token, String email) {}
```

### `dto/LinkResponse.java`

Replaces returning the raw `ShortLink` entity (avoids serializing the `User` object graph). All existing link endpoints return this type.

```java
package com.avivly.urlshortener.dto;

import java.time.LocalDateTime;

public record LinkResponse(
    Long id,
    String shortCode,
    String originalUrl,
    String strategy,
    boolean isActive,
    Integer maxClicks,
    int totalClicks,
    LocalDateTime expiresAt,
    String tags,
    LocalDateTime createdAt,
    Long ownerId
) {
    public static LinkResponse from(com.avivly.urlshortener.model.ShortLink link) {
        return new LinkResponse(
            link.getId(), link.getShortCode(), link.getOriginalUrl(),
            link.getStrategy(), link.isActive(), link.getMaxClicks(),
            link.getTotalClicks(), link.getExpiresAt(), link.getTags(),
            link.getCreatedAt(),
            link.getOwner() != null ? link.getOwner().getId() : null
        );
    }
}
```

### `security/JwtTokenProvider.java`

```java
package com.avivly.urlshortener.security;

import com.avivly.urlshortener.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;                    // HS256 key — must be ≥ 32 chars

    @Value("${jwt.expiry-ms:86400000}")
    private long expiryMs;                    // default 24 h

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiryMs))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

### `security/JwtAuthenticationFilter.java`

```java
package com.avivly.urlshortener.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

### `config/SecurityConfig.java`

> **CORS note:** `WebMvcConfig.addCorsMappings` is silently bypassed for requests intercepted by Spring Security's filter chain. CORS must be configured here via `.cors(Customizer.withDefaults())` and a `CorsConfigurationSource` bean. `WebMvcConfig` can remain unchanged (it covers unauthenticated MVC routes).

```java
package com.avivly.urlshortener.config;

import com.avivly.urlshortener.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import static org.springframework.http.HttpMethod.*;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(a -> a
                .requestMatchers(POST,   "/api/auth/**").permitAll()
                .requestMatchers(GET,    "/api/links").permitAll()
                .requestMatchers(GET,    "/api/strategies").permitAll()
                .requestMatchers(GET,    "/api/r/**").permitAll()
                .requestMatchers(GET,    "/{shortCode}").permitAll()
                .requestMatchers(GET,    "/actuator/**").permitAll()
                .requestMatchers(POST,   "/api/links").authenticated()
                .requestMatchers(PUT,    "/api/links/**").authenticated()
                .requestMatchers(DELETE, "/api/links/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### `service/AuthService.java`

```java
package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.*;
import com.avivly.urlshortener.model.User;
import com.avivly.urlshortener.repository.UserRepository;
import com.avivly.urlshortener.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(AuthRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .build();
        userRepo.save(user);
        return new AuthResponse(jwtTokenProvider.generateToken(user), user.getEmail());
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepo.findByEmail(req.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(jwtTokenProvider.generateToken(user), user.getEmail());
    }
}
```

### `controller/AuthController.java`

```java
package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.dto.*;
import com.avivly.urlshortener.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

---

## Modified Backend Components

### `service/LinkService.java`

**`create` signature change:**

```java
// Before
public ShortLink create(CreateLinkRequest req)

// After
public ShortLink create(CreateLinkRequest req, Long callerId)
```

After building `partialEntity` and before any `repo.save()`:

```java
User owner = userRepo.findById(callerId)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
partialEntity.setOwner(owner);
```

Add `private final UserRepository userRepo;` field.

**`update` signature change:**

```java
public ShortLink update(Long id, UpdateLinkRequest req, Long callerId)
```

After `repo.findById`, before patching fields:

```java
if (!link.getOwner().getId().equals(callerId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
}
```

> **Note:** Do not use `AccessDeniedException` here. Spring Security 6 intercepts that exception before it reaches `@RestControllerAdvice`, so the existing `handleResponseStatus` handler in `GlobalExceptionHandler` would never see it. `ResponseStatusException(FORBIDDEN)` is caught by the existing handler — no new handler needed.

**`delete` signature change:**

```java
public void delete(Long id, Long callerId)
```

After `repo.findById`, before `evictCache`:

```java
if (!link.getOwner().getId().equals(callerId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
}
```

### `controller/LinkController.java`

Add helper and update all write methods:

```java
private Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (Long) auth.getPrincipal();
}

// create:
return ResponseEntity.status(HttpStatus.CREATED)
    .body(LinkResponse.from(linkService.create(req, currentUserId())));

// getAll — map to LinkResponse:
public List<LinkResponse> getAll() {
    return linkService.findAll().stream().map(LinkResponse::from).toList();
}

// update:
return LinkResponse.from(linkService.update(id, req, currentUserId()));

// delete:
linkService.delete(id, currentUserId());
```

### `config/GlobalExceptionHandler.java`

No changes needed. The existing `handleResponseStatus` handler already maps `ResponseStatusException` (including 403) to the correct response.

### `UrlShortenerApplication.java`

Adding `spring-boot-starter-security` triggers `UserDetailsServiceAutoConfiguration`, which generates a random password and logs it at WARN. Since the JWT filter sets the principal directly (no `UserDetailsService` is used), exclude the auto-config:

```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
```

### `config/WebMvcConfig.java`

```java
// Before
.allowedOriginPatterns("*")

// After
.allowedOrigins("${cors.allowed-origin:http://localhost:5173}")
```

Add `@Value("${cors.allowed-origin:http://localhost:5173}") private String allowedOrigin;` and reference it.

---

## Configuration Changes

### `application.yml` additions

```yaml
jwt:
  secret: ${JWT_SECRET}                    # required; ≥ 32-char random string
  expiry-ms: ${JWT_EXPIRY_MS:86400000}     # default 24 h

cors:
  allowed-origin: ${CORS_ALLOWED_ORIGIN:http://localhost:5173}
```

### `application-test.yml` additions

```yaml
jwt:
  secret: test-secret-key-that-is-at-least-32-characters
  expiry-ms: 3600000

cors:
  allowed-origin: http://localhost:5173
```

### `pom.xml` additions

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

---

## API Contracts

### `POST /api/auth/register`

```
Request body:
  { "email": "alice@example.com", "password": "secret123" }

201 Created:
  { "token": "<jwt>", "email": "alice@example.com" }

409 Conflict:
  { "message": "Email already registered" }

400 Bad Request:
  { "message": "email: must be a well-formed email address; password: size must be between 8 and 2147483647" }
```

### `POST /api/auth/login`

```
Request body:
  { "email": "alice@example.com", "password": "secret123" }

200 OK:
  { "token": "<jwt>", "email": "alice@example.com" }

401 Unauthorized:
  { "message": "Invalid credentials" }
```

### `POST /api/links` (now protected)

```
Header:  Authorization: Bearer <jwt>

201 Created:
  {
    "id": 42, "shortCode": "abc123", "originalUrl": "https://...",
    "strategy": "RANDOM_BASE62", "isActive": true, "maxClicks": null,
    "totalClicks": 0, "expiresAt": null, "tags": null,
    "createdAt": "2026-06-07T10:00:00", "ownerId": 7
  }

401 Unauthorized: (no token or invalid token — from Spring Security, not application code)
```

### `PUT /api/links/{id}` (now protected + ownership)

```
Header:  Authorization: Bearer <jwt>

200 OK:   LinkResponse
401:      no or invalid token
403:      { "message": "Not the owner of this link" }
404:      { "message": "Link not found: {id}" }
```

### `DELETE /api/links/{id}` (now protected + ownership)

```
Header:  Authorization: Bearer <jwt>

204 No Content
401: no or invalid token
403: { "message": "Not the owner of this link" }
404: { "message": "Link not found: {id}" }
```

### `GET /api/links` (still public, response shape changes)

```
200 OK: List<LinkResponse>   (same fields as above, ownerId added)
```

---

## Frontend Changes

### `frontend/src/api.js`

Add interceptors and auth functions (after the axios instance creation):

```js
// Attach token on every outgoing request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Redirect to /login on 401
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const register = (data) => api.post('/auth/register', data);
export const login    = (data) => api.post('/auth/login', data);

export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('email');
  window.location.href = '/login';
};

// Decode userId from JWT without a library (sub claim is a stringified Long)
export const currentUserId = () => {
  const token = localStorage.getItem('token');
  if (!token) return null;
  try {
    return Number(JSON.parse(atob(token.split('.')[1])).sub);
  } catch {
    return null;
  }
};
```

### `frontend/src/components/LoginForm.jsx`

- `<form>` with email and password inputs
- On submit: call `login({ email, password })`, on success store `token` and `email` in `localStorage`, navigate to `/`
- On 401: show inline error "Invalid email or password"
- Footer link: "Don't have an account? Register"

### `frontend/src/components/RegisterForm.jsx`

- `<form>` with email, password, and confirm-password inputs
- Client-side check: password === confirmPassword before submitting
- On submit: call `register({ email, password })`, on success store token, navigate to `/`
- On 409: show inline error "Email is already taken"
- Footer link: "Already have an account? Log in"

### `frontend/src/App.jsx`

```jsx
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import { login as loginApi, logout, currentUserId } from './api';

// In header, add:
{localStorage.getItem('token') && (
  <button onClick={logout} className="...">Log out</button>
)}

// Pass currentUserId to LinksTable:
<LinksTable
  links={links}
  onEdit={setEditTarget}
  onDelete={handleDelete}
  onViewStats={setAnalyticsShortCode}
  tagFilter={tagFilter}
  onTagFilter={setTagFilter}
  currentUserId={currentUserId()}
/>

// Add routes:
<Route path="/login"    element={<LoginForm />} />
<Route path="/register" element={<RegisterForm />} />
```

### `frontend/src/components/LinksTable.jsx`

Add `currentUserId` to props and gate Edit/Delete:

```jsx
export default function LinksTable({ links, onEdit, onDelete, onViewStats,
                                     tagFilter, onTagFilter, currentUserId }) {
  // ...
  // Replace unconditional Edit/Delete buttons with:
  {link.ownerId === currentUserId && (
    <>
      <button onClick={() => onEdit(link)} className="text-xs text-yellow-600 hover:underline">
        Edit
      </button>
      <button onClick={() => onDelete(link.id)} className="text-xs text-red-600 hover:underline">
        Delete
      </button>
    </>
  )}
}
```

---

## Test Plan

### Backend

Follow existing patterns: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, `TestRestTemplate`, AssertJ.

#### `AuthControllerIntegrationTest.java` (new)

| Test | Assertion |
|------|-----------|
| `register_withValidData_returns201AndToken` | status 201, body has non-blank `token` and matching `email` |
| `register_duplicateEmail_returns409` | second register with same email → 409 |
| `login_withCorrectCredentials_returns200AndToken` | status 200, non-blank token |
| `login_withWrongPassword_returns401` | status 401 |
| `login_withUnknownEmail_returns401` | status 401 |

#### `LinkAuthorizationIntegrationTest.java` (new)

Shared helpers:

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

| Test | Assertion |
|------|-----------|
| `createLink_withoutToken_returns401` | 401 |
| `createLink_withValidToken_returns201AndOwnerIdSet` | 201, `ownerId` == authenticated user's id |
| `updateLink_byOwner_returns200` | 200 |
| `updateLink_byNonOwner_returns403` | 403 |
| `updateLink_withoutToken_returns401` | 401 |
| `deleteLink_byOwner_returns204` | 204 |
| `deleteLink_byNonOwner_returns403` | 403 |
| `deleteLink_withoutToken_returns401` | 401 |
| `getLinks_withoutToken_returns200` | 200, public route unchanged |
| `redirect_withoutToken_follows302` | redirect still works unauthenticated |

#### `LinkControllerIntegrationTest.java` (modify)

All tests that call `POST /api/links` must now register a user first and include a Bearer token. Add a `@BeforeEach` that registers a user and stores the token:

```java
private String token;

@BeforeEach
void setUp() {
    token = registerAndGetToken("test-" + UUID.randomUUID() + "@example.com");
}
```

Then pass `bearerHeaders(token)` as the `HttpEntity` on every mutating call.

### Frontend

#### `LoginForm.test.jsx` (new)

- Renders email and password inputs
- Successful submit calls `login()`, stores token, navigates to `/`
- 401 response shows error message

#### `RegisterForm.test.jsx` (new)

- Renders all three fields
- Password mismatch shows client-side error without calling API
- Successful submit stores token, navigates to `/`
- 409 response shows "Email is already taken"

---

## Implementation Order

1. Add `pom.xml` dependencies (security + jjwt)
2. Add `jwt.*` and `cors.*` properties to `application.yml` and `application-test.yml`
3. Write `V4__add_auth.sql`
4. Create `model/User.java` + `repository/UserRepository.java`
5. Create `security/JwtTokenProvider.java`
6. Create `dto/AuthRequest.java`, `dto/AuthResponse.java`
7. Create `service/AuthService.java`
8. Create `controller/AuthController.java`
9. Create `security/JwtAuthenticationFilter.java`
10. Create `config/SecurityConfig.java` (with CORS bean + `httpBasic`/`formLogin` disabled)
11. Modify `UrlShortenerApplication.java` — exclude `UserDetailsServiceAutoConfiguration`
12. Create `dto/LinkResponse.java`; update `LinkController.getAll()` to return `List<LinkResponse>`
13. Modify `model/ShortLink.java` — add `owner` field
14. Modify `service/LinkService.java` — add `callerId` param + `ResponseStatusException(FORBIDDEN)` checks
15. Modify `controller/LinkController.java` — `currentUserId()` helper, updated call sites
16. Run `mvn test` — all existing tests green (no `GlobalExceptionHandler` changes needed)
17. Write `AuthControllerIntegrationTest.java`
18. Write `LinkAuthorizationIntegrationTest.java`
19. Update `LinkControllerIntegrationTest.java` to authenticate
20. Frontend: update `api.js` (interceptors + auth exports)
21. Frontend: create `LoginForm.jsx`, `RegisterForm.jsx`
22. Frontend: update `App.jsx` (routes, logout, `currentUserId` prop)
23. Frontend: update `LinksTable.jsx` (conditional Edit/Delete)
24. Frontend: write `LoginForm.test.jsx`, `RegisterForm.test.jsx`

---

## Verification Checklist

```
[ ] mvn test — all backend tests green
[ ] docker-compose up — V4 migration applies cleanly; users table exists
[ ] POST /api/auth/register  → 201 + JWT
[ ] POST /api/auth/login     → 200 + JWT
[ ] POST /api/links (no token) → 401
[ ] POST /api/links (valid token) → 201, ownerId present in response
[ ] PUT  /api/links/{id} (owner token) → 200
[ ] PUT  /api/links/{id} (other user token) → 403
[ ] DELETE /api/links/{id} (no token) → 401
[ ] GET  /api/links (no token) → 200
[ ] GET  /{shortCode} (no token) → 302 redirect
[ ] npx vitest run — all frontend tests green
[ ] UI: Edit/Delete hidden for links owned by another user
[ ] UI: 401 from API clears token and redirects to /login
```
