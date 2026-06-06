# Phase 6 — CI/CD, Docker & Attribution (Tasks 6.1 + 6.2 + 6.3, parallel)

## Context

URL shortener project at `/home/aviv/dev/avivly/`.
Tasks 6.1, 6.2, and 6.3 are fully independent — run them with three parallel subagents.

**Prerequisites:** All previous phases complete and merged.

---

## Spawn three parallel subagents

---

### Subagent 1 — Task 6.1: CI — Download GeoLite2 for integration tests

**Goal:** Add a CI step that downloads the production `GeoLite2-City.mmdb` using a
stored `MAXMIND_LICENSE_KEY` secret before running integration tests.

Find the CI configuration file (`.github/workflows/*.yml`, `Jenkinsfile`, `.gitlab-ci.yml`,
or equivalent). If none exists, create `.github/workflows/ci.yml`.

Add a step before the integration-test step:

```yaml
- name: Download GeoLite2-City database
  env:
    MAXMIND_LICENSE_KEY: ${{ secrets.MAXMIND_LICENSE_KEY }}
  run: |
    curl -sSL \
      "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=${MAXMIND_LICENSE_KEY}&suffix=tar.gz" \
      -o GeoLite2-City.tar.gz
    tar -xzf GeoLite2-City.tar.gz --wildcards --no-anchored '*.mmdb' --strip-components=1
    mkdir -p backend/src/main/resources/geo
    mv GeoLite2-City.mmdb backend/src/main/resources/geo/
  # Skip gracefully if secret is not set (forks / PRs from external contributors)
  if: env.MAXMIND_LICENSE_KEY != ''
```

Add the corresponding env var for the integration-test step:
```yaml
- name: Run integration tests
  env:
    GEO_DB_PATH: src/main/resources/geo/GeoLite2-City.mmdb
  run: cd backend && ./mvnw verify
```

**Verify:** CI pipeline runs; integration tests pass with geo resolution active
when `MAXMIND_LICENSE_KEY` is set.

---

### Subagent 2 — Task 6.2: Docker — Volume mount and env var for production DB

**Goal:** Document/configure how to mount `GeoLite2-City.mmdb` into the container
and wire it to `GEO_DB_PATH`.

Edit `docker-compose.yml` (found at `/home/aviv/dev/avivly/docker-compose.yml`).

Add a volume and environment variable to the backend service:

```yaml
services:
  backend:
    # ... existing config ...
    environment:
      - GEO_DB_PATH=/data/GeoLite2-City.mmdb
      # ... existing env vars ...
    volumes:
      - ${MAXMIND_DB_PATH:-./geo/GeoLite2-City.mmdb}:/data/GeoLite2-City.mmdb:ro
```

This mounts the host file (defaulting to `./geo/GeoLite2-City.mmdb` relative to the
compose file) into the container as read-only at `/data/GeoLite2-City.mmdb`.

Create `geo/.gitkeep` and add `geo/*.mmdb` to `.gitignore` so the directory is tracked
but the DB file itself is not committed.

**Verify:** `docker compose config` shows the volume and env correctly.
With the `.mmdb` present at `./geo/GeoLite2-City.mmdb`:
`docker compose up backend` → `GET /actuator/health` shows `geoResolver.status: UP`.

---

### Subagent 3 — Task 6.3: MaxMind attribution

**Goal:** Add MaxMind attribution per the GeoLite2 EULA requirement.

Add attribution wherever the app has an About or legal page. If no such page exists,
add it to the frontend's footer or a dedicated `/about` route.

The required attribution text is:
> This product includes GeoLite2 data created by MaxMind, available from
> [https://www.maxmind.com](https://www.maxmind.com).

**If there is an existing footer or About component:** add the attribution there.

**If no such component exists:** create a minimal footer in `frontend/src/App.jsx`
or `frontend/src/components/Footer.jsx`:

```jsx
export default function Footer() {
  return (
    <footer className="text-xs text-gray-400 text-center py-4">
      This product includes GeoLite2 data created by MaxMind, available from{' '}
      <a href="https://www.maxmind.com" className="underline" target="_blank" rel="noopener noreferrer">
        maxmind.com
      </a>.
    </footer>
  );
}
```

Import and render `<Footer />` at the bottom of the main layout in `App.jsx`.

**Verify:** The attribution text is visible on the app's main page.

---

## After all three subagents finish

Review each change for correctness, then commit each independently:

- `feat: add CI step to download GeoLite2 DB (Phase 6.1)`
- `feat: configure Docker volume for GeoLite2 production DB (Phase 6.2)`
- `feat: add MaxMind GeoLite2 attribution (Phase 6.3)`
