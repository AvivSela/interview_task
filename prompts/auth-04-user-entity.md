# Agent Prompt: Create User Entity and UserRepository (AUTH-04)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + JPA).
Working directory: project root. V4 migration (AUTH-03) added the `users` table.
Existing entities live under `backend/src/main/java/com/avivly/urlshortener/model/`.
Existing repositories live under `backend/src/main/java/com/avivly/urlshortener/repository/`.

## Your Task
Create two new files.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/model/User.java`

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

### `backend/src/main/java/com/avivly/urlshortener/repository/UserRepository.java`

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

## Acceptance Criteria
- Both files compile (`mvn compile -f backend/pom.xml` exits 0)
- `User` entity maps to the `users` table with correct column names
