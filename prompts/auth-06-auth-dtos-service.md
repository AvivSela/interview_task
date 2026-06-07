# Agent Prompt: Create Auth DTOs and AuthService (AUTH-06)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-05 are done.
Existing DTOs live under `backend/src/main/java/com/avivly/urlshortener/dto/`.
Existing services live under `backend/src/main/java/com/avivly/urlshortener/service/`.

## Your Task
Create three new files: two DTOs and one service.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/dto/AuthRequest.java`

```java
package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.*;

public record AuthRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}
```

### `backend/src/main/java/com/avivly/urlshortener/dto/AuthResponse.java`

```java
package com.avivly.urlshortener.dto;

public record AuthResponse(String token, String email) {}
```

### `backend/src/main/java/com/avivly/urlshortener/service/AuthService.java`

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

## Notes
- `PasswordEncoder` is injected — it will be defined as a `@Bean` in `SecurityConfig` (AUTH-07).
  The app will not compile cleanly until AUTH-07 is also done.

## Acceptance Criteria
- All three files compile once AUTH-07 (SecurityConfig with PasswordEncoder bean) is also in place
- `register` throws 409 on duplicate email; `login` throws 401 on bad credentials
