# Agent Prompt: Create AuthController and SecurityConfig (AUTH-07)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + Spring Security 6).
Working directory: project root. AUTH-01 through AUTH-06 are done.
Existing controllers: `backend/src/main/java/com/avivly/urlshortener/controller/`.
Existing configs: `backend/src/main/java/com/avivly/urlshortener/config/` (WebMvcConfig, CacheConfig, etc.).

## Your Task
Create two new files.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/controller/AuthController.java`

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

### `backend/src/main/java/com/avivly/urlshortener/config/SecurityConfig.java`

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

## Notes
- **CORS**: `WebMvcConfig.addCorsMappings` is silently bypassed for requests intercepted by Spring Security's filter chain. CORS must live here via `.cors(Customizer.withDefaults())` + `CorsConfigurationSource` bean. `WebMvcConfig` can remain unchanged.
- **No UserDetailsService**: The JWT filter sets the principal directly. Spring Security auto-config will log a random password warning until AUTH-08 excludes `UserDetailsServiceAutoConfiguration`.

## Acceptance Criteria
- Both files compile
- `mvn compile -f backend/pom.xml` exits 0
- POST `/api/auth/register` and `/api/auth/login` are publicly accessible
- POST/PUT/DELETE `/api/links/**` require a Bearer token
