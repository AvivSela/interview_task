# Fix 2 — `GeoConfig` directory guard for Docker mount

## Context

**Prerequisites:** Fixes #10, #8, #7, #6 applied.

`backend/src/main/java/com/avivly/urlshortener/config/GeoConfig.java:28`

When `./geo/GeoLite2-City.mmdb` does not exist on the host, Docker bind-mount creates a **directory** at that path. `file.exists()` returns `true` (a directory exists), the "not found" guard is skipped, and `DatabaseReader.Builder(file).build()` throws `IOException` — producing a generic "Failed to open MaxMind DB" message instead of an actionable diagnosis.

## Objective

Add an `isDirectory()` check immediately after the blank-path guard and before the `exists()` guard, with a log message that names the Docker cause.

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/config/GeoConfig.java`.

Replace this section:

```java
File file = new File(path);
if (!file.exists()) {
    log.warn("MaxMind DB not found at {} — geo resolution disabled", path);
    return null;
}
```

With:

```java
File file = new File(path);
if (file.isDirectory()) {
    log.warn("geo.db.path points to a directory, not a file: {} — geo resolution disabled (Docker created a directory at this mount point?)", path);
    return null;
}
if (!file.exists()) {
    log.warn("MaxMind DB not found at {} — geo resolution disabled", path);
    return null;
}
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: detect Docker-created directory at geo.db.path in GeoConfig (#2)`
