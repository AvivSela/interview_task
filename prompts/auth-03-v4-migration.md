# Agent Prompt: Write V4 Flyway Migration (AUTH-03)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2 + PostgreSQL + Flyway).
Working directory: project root. AUTH-01 and AUTH-02 are done.
Existing migrations: V1 (baseline), V2 (geo_analytics), V3 (remove_pending_geo_status).
The `short_links` table exists with no `user_id` column. The `users` table does not exist yet.

## Your Task
Create `backend/src/main/resources/db/migration/V4__add_auth.sql`.

## File to Create

### `backend/src/main/resources/db/migration/V4__add_auth.sql`

```sql
-- Purge existing links (no owner to associate them with).
-- Acceptable for a pre-production system.
DELETE FROM short_links;

-- Users table
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- Add ownership FK to short_links
ALTER TABLE short_links
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);

CREATE INDEX idx_short_links_user_id ON short_links(user_id);
```

## Notes
- `DELETE FROM short_links` must run before the `NOT NULL` FK column is added — there are no users to backfill with.
- The tests run against H2 in PostgreSQL compatibility mode. `BIGSERIAL` is supported in that mode.

## Acceptance Criteria
- File exists at the correct path
- Flyway applies it cleanly (`docker-compose up` shows V4 applied)
- `users` table and `short_links.user_id` column exist after migration
