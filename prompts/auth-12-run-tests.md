# Agent Prompt: Run Backend Tests — Gate Check (AUTH-12)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-11 are all done (deps, config, migration, entities,
security, DTOs, service, controller updates).

## Your Task
Run the existing backend test suite and fix any failures before proceeding to write new tests.

```bash
cd backend && mvn test
```

## Expected failures at this point

The most likely failures are in `LinkControllerIntegrationTest` — existing tests call `POST /api/links`,
`PUT /api/links/{id}`, and `DELETE /api/links/{id}` **without** a Bearer token. Spring Security now returns
401 for those routes.

**Do NOT fix `LinkControllerIntegrationTest` here** — that is AUTH-15's job. If those tests fail with 401,
note it but do not patch the test file yet. All other failures are unexpected and must be fixed now.

## Fix unexpected failures if they appear

Common causes:
- Compile errors (missing imports, wrong method signatures)
- `ShortLink`-typed assertions in tests that now receive `LinkResponse` — update assertions to use the new field names if needed
- H2 migration issues with V4 SQL (check H2 compatibility mode)

## Acceptance Criteria
- `mvn test` exits 0 **OR** the only failures are 401s in `LinkControllerIntegrationTest` (which will be fixed in AUTH-15)
- No compile errors
- No unexpected test failures
