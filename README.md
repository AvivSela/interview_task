# URL Shortener

A full-stack URL shortener with click analytics. Create short links, set expiry dates and click limits, and track usage with time-series charts. Built with Spring Boot, React, and PostgreSQL.

## Features

- **Short link creation** — paste any URL and get a short code instantly; optionally supply your own custom slug
- **Three code strategies** — random Base62 (default), URL-derived hash, or sequential counter; each is configurable per link
- **Tags** — label links with comma-separated tags and filter the dashboard by tag
- **Click limits** — set a max-click cap; the link stops working automatically once it is reached
- **Expiry dates** — schedule a link to stop working at a specific date and time
- **Activate / deactivate** — disable a link at any time without deleting it, then re-enable it later
- **Click analytics** — per-link total click count and a day-by-day bar chart
- **Geo analytics** — top countries and cities breakdown (requires optional server-side setup)
- **QR codes** — generate a scannable QR code for any short link directly in the dashboard

For a full description of each feature see [docs/features.md](docs/features.md).

---

## Services

| Service | Image / Build | Port | Description |
|---------|--------------|------|-------------|
| `db` | `postgres:15-alpine` | `5432` | PostgreSQL database |
| `backend` | `./backend` | `8080` | Spring Boot REST API |
| `frontend` | `./frontend` | `3000` | React app served by nginx |

Startup order is enforced: `db` must pass its health check before `backend` starts, and `backend` must be up before `frontend`.

## Quick Start (Docker)

**Prerequisites:** Docker and Docker Compose installed.

```bash
# Build images and start all three services
docker-compose up --build

# Run in the background
docker-compose up --build -d
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Useful commands

```bash
# Stream logs for all services
docker-compose logs -f

# Stream logs for a single service
docker-compose logs -f backend

# Stop and remove containers (keeps the database volume)
docker-compose down

# Stop and wipe everything including the database volume
docker-compose down -v

# Rebuild a single service after a code change
docker-compose up --build backend
```

### Default credentials

| Variable | Value |
|----------|-------|
| Database name | `urlshortener` |
| Database user | `user` |
| Database password | `password` |

These are set in `docker-compose.yml` and injected into the backend at runtime. Change them there if needed — no other files need updating when running via Docker.

## Local Development (without Docker)

Use this when you want hot-reload for the backend or frontend without rebuilding images.

### Prerequisites

- Java 17+
- Maven 3.8+
- Node 18+
- PostgreSQL 15 running locally on port `5432`

### 1. Start PostgreSQL

Create the database and user to match the defaults:

```sql
CREATE USER "user" WITH PASSWORD 'password';
CREATE DATABASE urlshortener OWNER "user";
```

Or run a one-off Postgres container:

```bash
docker-compose up -d db
```

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`. It connects to `localhost:5432/urlshortener` by default (configured in `backend/src/main/resources/application.yml`). Override any value with environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/urlshortener \
SPRING_DATASOURCE_USERNAME=user \
SPRING_DATASOURCE_PASSWORD=password \
mvn spring-boot:run
```

Hibernate auto-creates and migrates the schema on startup (`ddl-auto: update`).

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The dev server starts on `http://localhost:5173`. All `/api/*` requests are proxied to `http://localhost:8080` via Vite's proxy (configured in `vite.config.js`), so no CORS setup is needed.

## API Reference

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| POST | `/api/links` | Create a short link | 201 Created |
| GET | `/api/links` | List all short links | 200 OK |
| PUT | `/api/links/{id}` | Update a short link | 200 OK |
| DELETE | `/api/links/{id}` | Delete a short link | 204 No Content |
| GET | `/api/links/{shortCode}/analytics` | Get click analytics | 200 OK |
| GET | `/{shortCode}` | Redirect to original URL | 302 Found / 410 Gone |

## AI Tools Disclosure

**Tools used:** Claude (Anthropic) — accessed via Claude Code CLI (`claude-sonnet-4-6`)

**How AI was used in this solution:**

- **Architecture & design:** Claude helped plan the overall structure of the application, including the separation between the Spring Boot backend and React frontend, and the URL shortening/redirect flow.

- **Prompt engineering & subagent design:** Claude was used to write and refine the development prompts themselves — designing them as a sequence of focused, modular prompts intended for use with AI subagents. These prompts can be found in the [`prompts/archive/`](./prompts/archive/) folder, covering each phase of development from scaffolding through integration and documentation.

- **Code generation:** Claude generated initial implementations for key components, including the backend controller logic, React component structure, and routing configuration.

- **Debugging:** Claude assisted in diagnosing and resolving issues encountered during development, such as redirect behavior, routing edge cases, and build configuration problems.

- **Documentation:** Claude helped draft code comments and written descriptions of the solution.

All AI-generated code was reviewed, tested, and integrated by me. Final design decisions and verification of correctness remained my responsibility.

## Assumptions

- **Single-user / trusted network only.** The API has no authentication or authorization. Anyone with network access can create, update, or delete any link. Do not expose it to the public internet without adding an auth layer.

- **Single backend instance.** The Caffeine cache is in-process (per-JVM). Running multiple backend replicas would give each its own independent cache, causing `totalClicks` to diverge across nodes and max-click limits to be under-enforced. Horizontal scaling requires replacing Caffeine with a shared cache (e.g. Redis).

- **Callers supply valid, absolute URLs.** `originalUrl` is only validated as non-blank. No scheme check, DNS resolution, or reachability check is performed.

- **Analytics are best-effort (at-most-once).** `recordClick` (increments `totalClicks`) and `logClickAsync` (writes a `ClickAnalytics` row) are independent operations. A JVM crash between them can leave `totalClicks` one ahead of `COUNT(ClickAnalytics)`. Occasional one-row drift is considered acceptable.

- **Real client IPs are not captured behind a reverse proxy.** `request.getRemoteAddr()` returns the proxy's IP (e.g. nginx in Docker), not the originating client IP. `X-Forwarded-For` is not read. IP analytics will be inaccurate in proxied deployments.

- **Tags are free-text labels, not structured data.** The `tags` field is a single plain-text string stored as-is. There is no "find all links by tag" query; tags are for display purposes only.

- **All timestamps are server-local with no timezone info.** `LocalDateTime` is used throughout. The assumption is that the server and any consumers share the same timezone, or that timezone differences are acceptable.

## Design Decisions

### Short code generation — pluggable strategy pattern

Three strategies are available, selectable per link. Each is a self-contained class behind a `CodeGenerationStrategy` interface; adding a new one requires only a new class and an enum entry.

| Strategy | How it works | Trade-off |
|----------|-------------|-----------|
| `RANDOM_BASE62` (default) | 7 cryptographically random Base62 characters via `SecureRandom` | Unpredictable; different codes for identical URLs |
| `HASH_TRUNCATE` | SHA-256 of the URL, first 7 bytes mapped to Base62 | Deterministic — same URL always yields the same code; collision possible across different URLs |
| `SEQUENTIAL` | Base62 encoding of the database-assigned row ID | Shortest possible codes that grow naturally; requires a two-phase save (see below) |

Custom aliases bypass all three strategies and are checked for uniqueness before saving.

### Two-phase save for sequential codes

`SEQUENTIAL` encodes the database-assigned `id`, which isn't available until the row exists. `LinkService.create` calls `saveAndFlush` to obtain the ID, generates the code, then saves again. If the generated code conflicts, the partial row is deleted and a `409 Conflict` is returned.

### Dual-track click counting

Clicks are tracked two independent ways:

- **`ShortLink.totalClicks`** — an integer on the entity, incremented with a targeted `UPDATE`. Used only for enforcing `maxClicks` limits.
- **`ClickAnalytics` rows** — one row per redirect, storing timestamp, referer, user agent, and IP. Used for time-series charts, top-referrer queries, and user-agent breakdowns.

Keeping these separate avoids aggregating the full `ClickAnalytics` table on every redirect just to check a limit.

### Cache eviction on every click

Short links are cached in Caffeine with a 10-minute TTL and a maximum of 10 000 entries. Because `totalClicks` lives on the cached entity, a stale entry would cause the max-click limit to be under-enforced. `recordClick` is annotated `@CacheEvict`, so the cache entry is invalidated after each redirect and the next lookup re-fetches a fresh count from the database.

### Asynchronous analytics writes

`AnalyticsService.logClickAsync` is annotated `@Async` and runs on a dedicated `analytics-*` thread pool (4 core threads, 10 max, 500-item queue). The redirect response is returned before the `ClickAnalytics` row is written, so a slow database write never adds latency to the redirect path.

### Validity check in the entity

`ShortLink.isValid()` centralizes the three validity conditions — active flag, expiry date, max-click limit — in the entity. The redirect controller calls this single method and returns `410 Gone` on failure. `410` is used instead of `404` to signal that the resource existed but is intentionally unavailable.

### Schema management

`ddl-auto: update` lets Hibernate manage the schema directly from entity definitions. No migration files are required at this scale, and the schema is fully reconstructible from the Java model classes.
