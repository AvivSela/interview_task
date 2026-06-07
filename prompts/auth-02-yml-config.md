# Agent Prompt: Add JWT & CORS Config Properties (AUTH-02)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 (pom.xml deps) is already done.
Two YAML files need new top-level property blocks appended.

## Your Task
Append `jwt.*` and `cors.*` property blocks to both config files.

## Changes

### `backend/src/main/resources/application.yml` — append at end

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiry-ms: ${JWT_EXPIRY_MS:86400000}

cors:
  allowed-origin: ${CORS_ALLOWED_ORIGIN:http://localhost:5173}
```

### `backend/src/test/resources/application-test.yml` — append at end

```yaml
jwt:
  secret: test-secret-key-that-is-at-least-32-characters
  expiry-ms: 3600000

cors:
  allowed-origin: http://localhost:5173
```

## Notes
- The production `JWT_SECRET` env var is required at runtime (no default). This is intentional.
- The test secret must be ≥ 32 characters for HS256 key derivation.

## Acceptance Criteria
- Both files contain the new blocks
- `mvn test -f backend/pom.xml` can still resolve all properties (no `Could not resolve placeholder` errors)
