# Task 6 — Update LinkService to use validateAndGenerate

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
`LinkService` is at `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java`.

All prior tasks (1–5a, 5b) are complete:
- `StrategyRegistry.validateAndGenerate(type, url, id, rawParams)` exists
- `CreateLinkRequest` now has a `strategyParams()` accessor returning `Map<String, Object>`
- `StrategyRegistry.generate(type, url, entity)` is still present as a shim but should be removed after this task

## Goal
Replace the two `strategyRegistry.generate(...)` calls in `LinkService.create()` with
`strategyRegistry.validateAndGenerate(...)`. Remove the import of `ShortLink` from the
strategy package (no longer needed there). Do not change any other logic in `LinkService`.

After this task: `mvn compile -pl backend` and `mvn test -pl backend` both exit 0.

---

## Current state of the relevant section in `LinkService.create()`

```java
if (strategyType == StrategyType.SEQUENTIAL) {
    ShortLink saved = repo.saveAndFlush(partialEntity);
    String code = strategyRegistry.generate(strategyType, req.originalUrl(), saved);
    if (repo.findByShortCode(code).isPresent()) {
        repo.delete(saved);
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
    }
    saved.setShortCode(code);
    return repo.save(saved);
}

String code = strategyRegistry.generate(strategyType, req.originalUrl(), partialEntity);
if (repo.findByShortCode(code).isPresent()) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
}
partialEntity.setShortCode(code);
return repo.save(partialEntity);
```

---

## Required changes (only these two call sites; touch nothing else)

Replace the sequential path call:
```java
// before
String code = strategyRegistry.generate(strategyType, req.originalUrl(), saved);

// after
String code = strategyRegistry.validateAndGenerate(
    strategyType, req.originalUrl(), saved.getId(), req.strategyParams());
```

Replace the non-sequential path call:
```java
// before
String code = strategyRegistry.generate(strategyType, req.originalUrl(), partialEntity);

// after
String code = strategyRegistry.validateAndGenerate(
    strategyType, req.originalUrl(), null, req.strategyParams());
```

After making these two replacements, remove the backward-compat `generate()` shim from
`StrategyRegistry.java` (it was only there to keep this class compiling; it is no longer needed).
Also remove the `import com.avivly.urlshortener.model.ShortLink` from `StrategyRegistry` if it
is only used by that shim.

---

## Done condition
`mvn compile -pl backend` exits 0.
`mvn test -pl backend` exits 0 — all existing tests pass.
