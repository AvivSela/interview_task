# Agent Prompt: Create JWT Security Components (AUTH-05)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + jjwt 0.12.6).
Working directory: project root. AUTH-01 through AUTH-04 are done (deps, config, migration, User entity).
Create a new package: `backend/src/main/java/com/avivly/urlshortener/security/`.

## Your Task
Create two new files that handle JWT creation and request-level authentication.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/security/JwtTokenProvider.java`

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
    private String secret;

    @Value("${jwt.expiry-ms:86400000}")
    private long expiryMs;

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

### `backend/src/main/java/com/avivly/urlshortener/security/JwtAuthenticationFilter.java`

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

## Acceptance Criteria
- Both files compile (`mvn compile -f backend/pom.xml` exits 0)
- `JwtAuthenticationFilter` extends `OncePerRequestFilter` and sets the userId (Long) as the principal
