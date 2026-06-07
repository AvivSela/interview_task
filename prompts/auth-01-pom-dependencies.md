# Agent Prompt: Add Auth Dependencies to pom.xml (AUTH-01)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + React + PostgreSQL).
Working directory: `backend/` (the Maven project root — `backend/pom.xml` exists).
No security dependencies exist yet. This is a pure `pom.xml` edit; no Java files change.

## Your Task
Add four dependencies to `backend/pom.xml` inside the existing `<dependencies>` block.

## Changes

### `backend/pom.xml` — add inside `<dependencies>`

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

## Acceptance Criteria
- `backend/pom.xml` contains all four new dependencies
- `mvn dependency:resolve -f backend/pom.xml` exits 0 (or `mvn compile` succeeds)
