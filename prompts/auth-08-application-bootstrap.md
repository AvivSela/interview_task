# Agent Prompt: Exclude UserDetailsServiceAutoConfiguration (AUTH-08)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-07 are done.
File to modify: `backend/src/main/java/com/avivly/urlshortener/UrlShortenerApplication.java`.

Adding `spring-boot-starter-security` triggers `UserDetailsServiceAutoConfiguration`, which generates a random
password and logs a WARN on startup. Since the JWT filter sets the principal directly (no `UserDetailsService`
is used), we exclude it.

## Your Task
Update `UrlShortenerApplication.java` — one annotation change and one import.

## Change

Current file:
```java
@SpringBootApplication
@EnableAsync
@EnableCaching
public class UrlShortenerApplication {
```

Updated file:
```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableAsync
@EnableCaching
public class UrlShortenerApplication {
```

Add import:
```java
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
```

## Acceptance Criteria
- `mvn compile -f backend/pom.xml` exits 0
- App starts without the "Using generated security password" WARN log
