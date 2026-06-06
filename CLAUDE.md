# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Analytics-driven URL shortener built with Spring Boot 3 (Java 17), React + Vite + Tailwind CSS, and PostgreSQL. Three services orchestrated by Docker Compose: `db` → `backend` → `frontend` (strict startup order enforced via healthchecks).

## Commands

### Running the full stack (Docker Compose)
```bash
cp .env.example .env                       # first time: fill in POSTGRES_PASSWORD
make dev                                   # build and start all services
make down                                  # stop and wipe the DB volume
docker-compose up --build backend          # rebuild a single service (no Makefile shortcut)
```

### Kubernetes deployment
```bash
cp .env.example .env           # fill in POSTGRES_PASSWORD (secrets sourced from .env)
make deploy-k8s                # apply namespace, upsert postgres-secret, apply all k8s/ manifests
kubectl -n avivly get all     # watch rollout status
```

`make deploy-k8s` runs three steps in order:
1. `kubectl apply -f k8s/namespace.yaml` — creates the `avivly` namespace
2. `kubectl create secret generic postgres-secret --from-env-file=.env ...` — upserts DB credentials as a K8s Secret
3. `kubectl apply -f k8s/` — applies all manifests (configmap, postgres StatefulSet, backend/frontend Deployments, nginx LoadBalancer)

**Images:** backend and frontend Deployments reference `avivly/backend:latest` and `avivly/frontend:latest` (`imagePullPolicy: IfNotPresent`). Build before deploying:
```bash
docker build -t avivly/backend:latest ./backend
docker build -t avivly/frontend:latest ./frontend
# minikube users: minikube image load avivly/backend:latest && minikube image load avivly/frontend:latest
```

**Startup ordering (K8s):** init containers replace `depends_on`. The backend init container waits for postgres:5432 via netcat; nginx init containers wait for backend:8080 and frontend:80 before starting.

**Entry point:** the nginx LoadBalancer Service exposes port 80. On minikube: `minikube service nginx -n avivly`.

### Backend (Spring Boot + Maven)
```bash
cd backend
mvn spring-boot:run                # start on :8080 (connects to localhost:5432)
mvn test                           # run all tests
mvn test -Dtest=RedirectIntegrationTest  # run a single test class
mvn clean package -DskipTests      # build the JAR
```

Tests use an in-memory H2 database (`@ActiveProfiles("test")`) — no running Postgres required.

### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev      # dev server on :5173; /api/* proxied to :8080
npm run build    # production build
npx vitest       # run tests
npx vitest run src/components/LinksTable.test.jsx  # run a single test file
```

## Architecture

### Backend package layout (`com.avivly.urlshortener`)
- `controller/` — `RedirectController` (public redirect at `/{shortCode}` and `/api/r/{shortCode}`), `LinkController` (CRUD at `/api/links`), `StrategyController` (strategy metadata)
- `service/` — `LinkService` (create/update/delete + Caffeine cache), `AnalyticsService` (query analytics; `logClickAsync` is `@Async`), `GeoResolverService` (optional MaxMind GeoIP2 lookup)
- `util/strategy/` — pluggable code generation via `CodeGenerationStrategy` interface; `StrategyRegistry` wires the three built-in strategies (`RANDOM_BASE62`, `HASH_TRUNCATE`, `SEQUENTIAL`)
- `config/` — `CacheConfig` (Caffeine, 10-min TTL, 10K entries), `AsyncConfig` (analytics thread pool: 4 core / 10 max / 500-queue, prefix `analytics-`), `GeoConfig` (optional `DatabaseReader` bean, null if path unset)
- `dto/` — request/response records (`CreateLinkRequest`, `UpdateLinkRequest`, `AnalyticsResponse`, `GeoResult`)
- `model/` — `ShortLink` (entity with `isValid()` gate), `ClickAnalytics`, `GeoStatus` (enum)

### Key design decisions
- **Redirect flow:** lookup → `isValid()` check → `recordClick()` (`@CacheEvict`) → `logClickAsync()` (fire-and-forget) → `302` to original URL. Invalid/unknown codes redirect to `/link-expired` (frontend route), not `410`.
- **Cache eviction on every click:** `totalClicks` lives on the cached `ShortLink` entity; `@CacheEvict` after each `recordClick` ensures max-click limits are enforced on the next lookup.
- **SEQUENTIAL strategy two-phase save:** `saveAndFlush` to get the DB-assigned ID, encode it to Base62, then save again. Conflict on second save → delete partial row, return `409`.
- **Geo resolution is optional:** `GeoConfig` emits a null `DatabaseReader` bean when `geo.db.path` is unset or missing. `GeoResolverService.isEnabled()` gates geo columns in analytics responses. Set `GEO_DB_PATH` env var (or `geo.db.path` in yml) to a MaxMind `GeoLite2-City.mmdb` file path to enable it.
- **Schema managed by Flyway:** migrations live in `backend/src/main/resources/db/migration/` (`V1__baseline.sql`, `V2__geo_analytics.sql`, `V3__remove_pending_geo_status.sql`). `ddl-auto: validate` in production (schema must match entities); `create-drop` in tests (H2).
- **IP extraction:** `RedirectController.extractClientIp` reads `X-Real-IP` first, then walks `X-Forwarded-For` rightmost-to-leftmost skipping private addresses. Falls back to `request.getRemoteAddr()`.

### Frontend
Single-page app with one real route (`/`), one error route (`/link-expired`), and a `path="*"` catch-all that renders the Not Found page for any other unmatched path. State lives in `App.jsx`: links list, current edit target, active analytics short code, and tag filter. `api.js` is an axios instance; all calls go through it. `AnalyticsPanel` is rendered inline below the table when a short code is selected — it is not a modal.

### Kubernetes manifests (`k8s/`)
- `namespace.yaml` — `avivly` namespace
- `configmap.yaml` — nginx.conf baked as a ConfigMap (rate-limit 30 req/min on redirect path; proxy routing mirrors Docker Compose nginx)
- `postgres.yaml` — StatefulSet (1 replica, 5Gi PVC) + ClusterIP Service; credentials from `postgres-secret`
- `backend.yaml` — Deployment + ClusterIP Service; init container waits on postgres:5432; geo disabled by default (no `GEO_DB_PATH`)
- `frontend.yaml` — Deployment + ClusterIP Service; stateless
- `nginx.yaml` — Deployment + LoadBalancer Service (port 80); init containers wait for backend and frontend; mounts nginx-config ConfigMap
- `secret.yaml.example` — documentation placeholder only; actual Secret is created by `make deploy-k8s` from `.env`

### Geo feature
The `geo/` directory at the project root contains documentation and scripts for setting up the MaxMind database. The test suite includes a real (sample) `GeoLite2-City-Test.mmdb` at `backend/src/test/resources/` used by `GeoAnalyticsIntegrationTest` and `GeoResolverServiceTest`. The `application-dev.yml` profile points to this test database for local development with geo enabled.
