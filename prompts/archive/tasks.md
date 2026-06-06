# Agent Task List — Analytics-Driven URL Shortener

Each task is self-contained with clear inputs, outputs, and acceptance criteria.
Tasks within the same phase can run in parallel unless marked with `depends_on`.

---

## Phase 1: Project Scaffolding

### TASK-01 — Scaffold Spring Boot backend
**Action:** Generate the Spring Boot project via Spring Initializr CLI or create `pom.xml` manually.

**Create:** `backend/pom.xml`

Requirements:
- Group: `com.avivly`, Artifact: `urlshortener`
- Java 17, Spring Boot 3.x
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-cache`, `spring-boot-starter-validation`, `postgresql`, `caffeine`
- Include `spring-boot-maven-plugin`

```xml
<!-- Caffeine dependency -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**Acceptance:** `mvn -f backend/pom.xml dependency:resolve` exits 0.

---

### TASK-02 — Scaffold React frontend
**Action:** Create Vite + React project structure.

**Create:** `frontend/` directory with:
- `frontend/package.json` with dependencies: `react`, `react-dom`, `axios`, `recharts`, `tailwindcss`, `@tailwindcss/vite`
- `frontend/vite.config.js` with proxy: `/api` → `http://localhost:8080`
- `frontend/index.html`
- `frontend/src/main.jsx`
- `frontend/src/App.jsx` (empty shell)
- `frontend/tailwind.config.js`
- `frontend/src/index.css` (tailwind directives)

**Acceptance:** `npm --prefix frontend install` exits 0. `npm --prefix frontend run build` produces `frontend/dist/`.

---

### TASK-03 — Create main Spring Boot application class
`depends_on: TASK-01`

**Create:** `backend/src/main/java/com/avivly/urlshortener/UrlShortenerApplication.java`

```java
@SpringBootApplication
@EnableAsync
@EnableCaching
public class UrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
```

**Acceptance:** File compiles (no missing imports).

---

### TASK-04 — Create `application.yml`
`depends_on: TASK-01`

**Create:** `backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/urlshortener}
    username: ${SPRING_DATASOURCE_USERNAME:user}
    password: ${SPRING_DATASOURCE_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  cache:
    type: caffeine

server:
  port: 8080

logging:
  level:
    root: WARN
    com.avivly: INFO
```

**Acceptance:** File is valid YAML. Environment variable placeholders allow Docker override.

---

## Phase 2: Backend — Data Layer

### TASK-05 — Create `ShortLink` entity
`depends_on: TASK-01`

**Create:** `backend/src/main/java/com/avivly/urlshortener/model/ShortLink.java`

Fields (map to `short_links` table):
- `Long id` — `@Id @GeneratedValue(IDENTITY)`
- `String shortCode` — `@Column(unique=true, nullable=false)`
- `String originalUrl` — `@Column(nullable=false, columnDefinition="TEXT")`
- `String strategy` — default `"RANDOM_BASE62"`
- `boolean isActive` — default `true`
- `Integer maxClicks` — nullable
- `int totalClicks` — default `0`
- `LocalDateTime expiresAt` — nullable
- `String tags`
- `LocalDateTime createdAt` — `@Column(updatable=false)`

Include `@PrePersist` that sets `createdAt = LocalDateTime.now()` if null.

Include `isValid()` method:
```java
public boolean isValid() {
    if (!isActive) return false;
    if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
    if (maxClicks != null && totalClicks >= maxClicks) return false;
    return true;
}
```

Use Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`.

**Acceptance:** Entity compiles, all fields have correct JPA annotations.

---

### TASK-06 — Create `ClickAnalytics` entity
`depends_on: TASK-01`

**Create:** `backend/src/main/java/com/avivly/urlshortener/model/ClickAnalytics.java`

Fields (map to `click_analytics` table):
- `Long id` — `@Id @GeneratedValue(IDENTITY)`
- `String shortCode` — `@Column(nullable=false)`
- `LocalDateTime clickedAt` — set via `@PrePersist`
- `String referer` — `@Column(columnDefinition="TEXT")`
- `String userAgent` — `@Column(columnDefinition="TEXT")`
- `String ipAddress` — `@Column(length=45)`

Use Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`.

**Acceptance:** Entity compiles.

---

### TASK-07 — Create `ShortLinkRepository`
`depends_on: TASK-05`

**Create:** `backend/src/main/java/com/avivly/urlshortener/repository/ShortLinkRepository.java`

```java
public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    Optional<ShortLink> findByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortLink s SET s.totalClicks = s.totalClicks + 1 WHERE s.shortCode = :shortCode")
    void incrementClicks(@Param("shortCode") String shortCode);
}
```

**Acceptance:** Interface compiles. `@Transactional` is NOT needed here (caller handles it).

---

### TASK-08 — Create `ClickAnalyticsRepository`
`depends_on: TASK-06`

**Create:** `backend/src/main/java/com/avivly/urlshortener/repository/ClickAnalyticsRepository.java`

```java
public interface ClickAnalyticsRepository extends JpaRepository<ClickAnalytics, Long> {
    List<ClickAnalytics> findByShortCodeOrderByClickedAtDesc(String shortCode);

    @Query("SELECT DATE(c.clickedAt) as date, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode GROUP BY DATE(c.clickedAt) ORDER BY DATE(c.clickedAt)")
    List<Object[]> countClicksByDay(@Param("shortCode") String shortCode);

    @Query("SELECT c.referer, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode AND c.referer IS NOT NULL GROUP BY c.referer ORDER BY count DESC")
    List<Object[]> topReferrers(@Param("shortCode") String shortCode);

    @Query("SELECT c.userAgent, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode AND c.userAgent IS NOT NULL GROUP BY c.userAgent ORDER BY count DESC")
    List<Object[]> topUserAgents(@Param("shortCode") String shortCode);
}
```

**Acceptance:** Interface compiles.

---

## Phase 3: Backend — Config & Utilities

### TASK-09 — Create `CacheConfig`
`depends_on: TASK-03`

**Create:** `backend/src/main/java/com/avivly/urlshortener/config/CacheConfig.java`

```java
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCache linksCache = new CaffeineCache("shortLinks",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build());
        return new SimpleCacheManager(List.of(linksCache));
    }
}
```

**Acceptance:** Compiles. `SimpleCacheManager` imported from `org.springframework.cache.support`.

---

### TASK-10 — Create `AsyncConfig`
`depends_on: TASK-03`

**Create:** `backend/src/main/java/com/avivly/urlshortener/config/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("analytics-");
        executor.initialize();
        return executor;
    }
}
```

**Acceptance:** Compiles. Thread pool name prefix is `analytics-`.

---

### TASK-11 — Create `WebMvcConfig` (CORS)
`depends_on: TASK-03`

**Create:** `backend/src/main/java/com/avivly/urlshortener/config/WebMvcConfig.java`

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:3000", "http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
```

**Acceptance:** Compiles. Allows both Vite dev port (5173) and Docker port (3000).

---

### TASK-12 — Create `Base62` utility
`depends_on: TASK-01`

**Create:** `backend/src/main/java/com/avivly/urlshortener/util/Base62.java`

```java
public class Base62 {
    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        return IntStream.range(0, length)
            .mapToObj(i -> String.valueOf(CHARS.charAt(RANDOM.nextInt(62))))
            .collect(Collectors.joining());
    }
}
```

**Acceptance:** Compiles. `generate(7)` produces a 7-character alphanumeric string.

---

## Phase 4: Backend — DTOs

### TASK-13 — Create request/response DTOs
`depends_on: TASK-01`

**Create** the following in `backend/src/main/java/com/avivly/urlshortener/dto/`:

**`CreateLinkRequest.java`**
```java
public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
```

**`UpdateLinkRequest.java`**
```java
public record UpdateLinkRequest(
    String originalUrl,
    Boolean isActive,
    LocalDateTime expiresAt,
    String tags,
    Integer maxClicks
) {}
```

**`AnalyticsResponse.java`**
```java
public record AnalyticsResponse(
    int totalClicks,
    List<DailyCount> clicksOverTime,
    List<ReferrerCount> topReferrers,
    List<AgentCount> topUserAgents
) {
    public record DailyCount(String date, long count) {}
    public record ReferrerCount(String referer, long count) {}
    public record AgentCount(String userAgent, long count) {}
}
```

**Acceptance:** All records compile. `@NotBlank` imported from `jakarta.validation`.

---

## Phase 5: Backend — Service Layer

### TASK-14 — Create `LinkService`
`depends_on: TASK-07, TASK-09, TASK-12, TASK-13`

**Create:** `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java`

Implement:
- `@Cacheable(value="shortLinks", key="#shortCode") ShortLink findByShortCode(String shortCode)`
- `List<ShortLink> findAll()`
- `ShortLink create(CreateLinkRequest req)` — use `customAlias` if provided, else `Base62.generate(7)`; check for alias collision and throw `ResponseStatusException(CONFLICT)` if taken
- `ShortLink update(Long id, UpdateLinkRequest req)` — update non-null fields, `@CacheEvict` on `shortCode`
- `void delete(Long id)` — `@CacheEvict` on `shortCode` before delete

**Acceptance:** `@CacheEvict` is called on every mutating operation. Cache key is always `shortCode` (not `id`).

---

### TASK-15 — Create `AnalyticsService`
`depends_on: TASK-08, TASK-10, TASK-13`

**Create:** `backend/src/main/java/com/avivly/urlshortener/service/AnalyticsService.java`

```java
@Service
public class AnalyticsService {

    @Async
    @Transactional
    public void logClickAsync(String shortCode, String referer, String userAgent, String ip) {
        clickRepo.save(ClickAnalytics.builder()
            .shortCode(shortCode)
            .referer(referer)
            .userAgent(userAgent)
            .ipAddress(ip)
            .build());
        linkRepo.incrementClicks(shortCode);
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        // query clicksOverTime, topReferrers, topUserAgents from repo
        // map Object[] results to AnalyticsResponse records
        // get totalClicks from ShortLink.totalClicks
    }
}
```

**Acceptance:** `logClickAsync` is annotated `@Async` and `@Transactional`. `getAnalytics` maps all three query results correctly.

---

## Phase 6: Backend — Controller Layer

### TASK-16 — Create `RedirectController`
`depends_on: TASK-14, TASK-15`

**Create:** `backend/src/main/java/com/avivly/urlshortener/controller/RedirectController.java`

```java
@RestController
public class RedirectController {

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        ShortLink link = linkService.findByShortCode(shortCode);
        if (link == null || !link.isValid()) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        analyticsService.logClickAsync(
            shortCode,
            request.getHeader("Referer"),
            request.getHeader("User-Agent"),
            request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getOriginalUrl()))
                .build();
    }
}
```

**Acceptance:** Responds `302` on valid code, `410` on invalid/expired/exhausted. Never blocks on analytics.

---

### TASK-17 — Create `LinkController`
`depends_on: TASK-14, TASK-15`

**Create:** `backend/src/main/java/com/avivly/urlshortener/controller/LinkController.java`

Implement at `/api/links`:
- `POST /api/links` → `linkService.create(req)` → `201 Created`
- `GET /api/links` → `linkService.findAll()` → `200 OK`
- `PUT /api/links/{id}` → `linkService.update(id, req)` → `200 OK`
- `DELETE /api/links/{id}` → `linkService.delete(id)` → `204 No Content`
- `GET /api/links/{shortCode}/analytics` → `analyticsService.getAnalytics(shortCode)` → `200 OK`

Use `@Validated` on POST/PUT request bodies.

**Acceptance:** All 5 endpoints exist. POST returns `201`, DELETE returns `204`.

---

## Phase 7: Frontend Components

### TASK-18 — Create `api.js`
`depends_on: TASK-02`

**Create:** `frontend/src/api.js`

```js
import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

export const getLinks = () => api.get('/links');
export const createLink = (data) => api.post('/links', data);
export const updateLink = (id, data) => api.put(`/links/${id}`, data);
export const deleteLink = (id) => api.delete(`/links/${id}`);
export const getAnalytics = (shortCode) => api.get(`/links/${shortCode}/analytics`);
```

**Acceptance:** All 5 functions exported. Base URL is `/api` (proxied by Vite/nginx, not hardcoded to port 8080).

---

### TASK-19 — Create `LinkForm` component
`depends_on: TASK-18`

**Create:** `frontend/src/components/LinkForm.jsx`

Props: `{ onCreated, editTarget, onUpdated, onCancel }`

Fields (controlled inputs):
- `originalUrl` — text, required
- `customAlias` — text, optional
- `strategy` — `<select>` with options: `RANDOM_BASE62`, `CUSTOM`
- `expiresAt` — datetime-local input, optional
- `maxClicks` — number input, optional
- `tags` — text input, optional

On submit: call `createLink` or `updateLink` (based on whether `editTarget` is set), then call `onCreated`/`onUpdated`.

**Acceptance:** Form clears after successful submit. Shows inline error if request fails.

---

### TASK-20 — Create `LinksTable` component
`depends_on: TASK-18`

**Create:** `frontend/src/components/LinksTable.jsx`

Props: `{ links, onEdit, onDelete, onViewStats }`

Renders a `<table>` with columns:
- Short Link (clickable, opens `http://localhost:8080/{shortCode}` in new tab)
- Original URL (truncated to 40 chars)
- Clicks (`totalClicks`)
- Status (`isActive` → green "Active" badge / red "Inactive" badge)
- Created (`createdAt` formatted as `YYYY-MM-DD`)
- Actions: `[Edit]` `[Delete]` `[Stats]` buttons

**Acceptance:** Table renders empty state message when `links` is empty. Delete triggers `window.confirm` before calling `onDelete`.

---

### TASK-21 — Create `AnalyticsPanel` component
`depends_on: TASK-18`

**Create:** `frontend/src/components/AnalyticsPanel.jsx`

Props: `{ shortCode, onClose }`

On mount: fetches `getAnalytics(shortCode)`.

Renders:
- Total clicks count (large number display)
- `<LineChart>` from `recharts` with `clicksOverTime` data (`date` on X axis, `count` on Y axis)
- Two lists side by side: Top Referrers and Top User Agents (top 5 each)
- `[Close]` button that calls `onClose`

Show loading spinner while fetching. Show error message if fetch fails.

**Acceptance:** `recharts` `LineChart` is used. Component handles empty `clicksOverTime` array gracefully (renders empty chart, not crash).

---

### TASK-22 — Wire `App.jsx`
`depends_on: TASK-19, TASK-20, TASK-21`

**Edit:** `frontend/src/App.jsx`

State:
- `links` — array, fetched on mount and after every mutation
- `editTarget` — link object or null (drives form into edit mode)
- `analyticsShortCode` — string or null (drives analytics panel visibility)

Layout:
```
<header> URL Shortener </header>
<main>
  <div class="grid grid-cols-2">
    <LinkForm ... />
    <LinksTable ... />
  </div>
  {analyticsShortCode && <AnalyticsPanel ... />}
</main>
```

**Acceptance:** Creating a link refreshes the table. Clicking Stats on a row opens the analytics panel for that row's `shortCode`.

---

## Phase 8: Docker & Deployment

### TASK-23 — Create `backend/Dockerfile`
`depends_on: TASK-01`

**Create:** `backend/Dockerfile`

```dockerfile
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Acceptance:** `docker build ./backend` completes successfully.

---

### TASK-24 — Create `frontend/Dockerfile` and `nginx.conf`
`depends_on: TASK-02`

**Create:** `frontend/Dockerfile`

```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Create:** `frontend/nginx.conf`

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**Acceptance:** Nginx proxies `/api/` to the backend service. SPA routing handled by `try_files`.

---

### TASK-25 — Create `docker-compose.yml`
`depends_on: TASK-23, TASK-24`

**Create:** `docker-compose.yml` at project root.

```yaml
version: '3.8'
services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: urlshortener
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d urlshortener"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/urlshortener
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: password
      SPRING_JPA_HIBERNATE_DDL_AUTO: update

  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend
```

**Acceptance:** `docker-compose config` validates without error. Backend waits for DB healthcheck before starting.

---

## Phase 9: Tests

### TASK-26 — Write integration tests for redirect flow
`depends_on: TASK-14, TASK-15, TASK-16`

**Create:** `backend/src/test/java/com/avivly/urlshortener/RedirectIntegrationTest.java`

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`.

Write tests for:
1. **Happy path** — create a link, GET `/{shortCode}`, assert `302` and `Location` header equals original URL
2. **Expired link** — create a link with `expiresAt` in the past, GET `/{shortCode}`, assert `410`
3. **Click exhausted** — create a link with `maxClicks=1`, GET `/{shortCode}` twice, assert second request returns `410`
4. **Unknown code** — GET `/nonexistent`, assert `410`

Use `H2` in-memory DB for tests (add `h2` test-scope dependency, `application-test.yml` with `spring.datasource.url=jdbc:h2:mem:test`).

**Acceptance:** All 4 tests pass with `mvn test`.

---

## Phase 10: README

### TASK-27 — Write `README.md`
`depends_on: TASK-25`

**Create:** `README.md` at project root.

Include:
1. **Overview** — one paragraph describing the app
2. **Quick Start** (single command):
   ```bash
   docker-compose up --build
   ```
   Then open `http://localhost:3000`
3. **API Reference** — table of all endpoints (from §3 of requirement.md)
4. **Local Development** — how to run backend (`mvn spring-boot:run`) and frontend (`npm run dev`) separately without Docker
5. **Architecture Notes** — 2-3 sentences on caching (`@Cacheable`) and async analytics (`@Async`)

**Acceptance:** Someone unfamiliar with the project can get it running from README alone.

---

## Dependency Graph (execution order)

```
TASK-01 ──┬─ TASK-03 ──┬─ TASK-09
           │             ├─ TASK-10
           │             └─ TASK-11
           ├─ TASK-04
           ├─ TASK-05 ── TASK-07 ──┬─ TASK-14 ──┬─ TASK-16
           ├─ TASK-06 ── TASK-08 ──┤              ├─ TASK-17
           ├─ TASK-12               └─ TASK-15 ──┘
           └─ TASK-13
                                   TASK-14 + TASK-15 + TASK-16/17 ── TASK-26

TASK-02 ──┬─ TASK-18 ──┬─ TASK-19 ──┐
           │             ├─ TASK-20 ──┼─ TASK-22
           │             └─ TASK-21 ──┘
           └─ TASK-24

TASK-23 (backend Dockerfile) ──┬─ TASK-25
TASK-24 (frontend Dockerfile) ─┘

TASK-25 ── TASK-27
```

## Summary

| Phase | Tasks | Parallelizable |
|-------|-------|----------------|
| 1 — Scaffolding | TASK-01 to TASK-04 | TASK-01 and TASK-02 in parallel |
| 2 — Data layer | TASK-05 to TASK-08 | All 4 in parallel after TASK-01 |
| 3 — Config & utils | TASK-09 to TASK-13 | All 5 in parallel after TASK-03 |
| 4 — DTOs | TASK-13 | After TASK-01 |
| 5 — Services | TASK-14, TASK-15 | In parallel after deps |
| 6 — Controllers | TASK-16, TASK-17 | In parallel after services |
| 7 — Frontend | TASK-18 to TASK-22 | TASK-18–21 in parallel, TASK-22 last |
| 8 — Docker | TASK-23 to TASK-25 | TASK-23 and TASK-24 in parallel |
| 9 — Tests | TASK-26 | After controllers |
| 10 — README | TASK-27 | After TASK-25 |
