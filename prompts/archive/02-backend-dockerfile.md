# Agent Prompt: Backend Dockerfile (TASK-23)

## Project Context
You are building an **analytics-driven URL shortener**.
The backend Maven project exists at `backend/pom.xml` — Spring Boot 3.x, Java 17.
The main class is `com.avivly.urlshortener.UrlShortenerApplication`.

## Your Task
Create the multi-stage Docker build file for the backend.

## Files to Create

### `backend/Dockerfile`
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

## Acceptance Criteria
- File is at `backend/Dockerfile` (not `backend/backend/Dockerfile`)
- Two-stage build: Maven build stage → slim JRE runtime stage
- Build stage uses `maven:3.8.8-eclipse-temurin-17`
- Runtime stage uses `eclipse-temurin:17-jre-jammy`
- Tests are skipped in the build step with `-DskipTests`
- Port 8080 is exposed
- `docker build ./backend` completes successfully (verify if Docker is available)
