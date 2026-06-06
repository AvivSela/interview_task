# Agent Prompt: Spring Boot App Class & application.yml (TASK-03, TASK-04)

## Project Context
You are building an **analytics-driven URL shortener**.
The backend Maven project already exists at `backend/pom.xml` with group `com.avivly`, artifact `urlshortener`, Java 17, Spring Boot 3.x.

## Your Task
Create the main Spring Boot application entry point and the YAML configuration file.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/UrlShortenerApplication.java`
```java
package com.avivly.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
public class UrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
```

### `backend/src/main/resources/application.yml`
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

## Acceptance Criteria
- `UrlShortenerApplication.java` has all three annotations: `@SpringBootApplication`, `@EnableAsync`, `@EnableCaching`
- All imports are fully qualified (no missing imports)
- `application.yml` is valid YAML
- All datasource values use `${ENV_VAR:default}` syntax to allow Docker override via environment variables
- Cache type is set to `caffeine`
