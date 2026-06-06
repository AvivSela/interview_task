# Task 4 — Replace StrategyRegistry

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Strategy classes live in `backend/src/main/java/com/avivly/urlshortener/util/strategy/`.

The following already exist from prior tasks:
- `ParamType`, `StrategyParamDefinition`, `StrategyParamValidator`
- Updated `CodeGenerationStrategy` interface (signature: `generate(String, Long, Map<String,Object>)` + `paramSchema()`)
- Updated `RandomBase62Strategy`, `HashTruncateStrategy`, `SequentialStrategy`

`StrategyRegistry` currently has a temporary stub that compiles but doesn't validate params.
This task replaces it with the real implementation.

## Goal
Replace `StrategyRegistry.java` with the version that injects `StrategyParamValidator`,
exposes `validateAndGenerate()` as the single entry point, and adds `getSchema()`/`getAllSchemas()`.
`LinkService` currently calls `strategyRegistry.generate(type, url, entity)` — keep that method
present as a thin delegate to `validateAndGenerate` so `LinkService` still compiles.
`LinkService` is updated in Task 6; for now just keep it from breaking.

After this task the project must compile (`mvn compile -pl backend`).

---

## Current state of `StrategyRegistry.java`
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class StrategyRegistry {

    private final Map<StrategyType, CodeGenerationStrategy> strategies;

    public StrategyRegistry() {
        strategies = new EnumMap<>(StrategyType.class);
        strategies.put(StrategyType.RANDOM_BASE62, new RandomBase62Strategy());
        strategies.put(StrategyType.HASH_TRUNCATE, new HashTruncateStrategy());
        strategies.put(StrategyType.SEQUENTIAL,    new SequentialStrategy());
    }

    public String generate(StrategyType type, String url, ShortLink entity) {
        CodeGenerationStrategy strategy = strategies.getOrDefault(type, strategies.get(StrategyType.RANDOM_BASE62));
        Long id = entity != null ? entity.getId() : null;
        return strategy.generate(url, id, java.util.Map.of());
    }
}
```

---

## New state of `StrategyRegistry.java` (replace entirely)

**Path:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyRegistry.java`

```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StrategyRegistry {

    private final StrategyParamValidator validator;
    private final Map<StrategyType, CodeGenerationStrategy> strategies;

    public StrategyRegistry(StrategyParamValidator validator) {
        this.validator = validator;
        this.strategies = new EnumMap<>(StrategyType.class);
        strategies.put(StrategyType.RANDOM_BASE62, new RandomBase62Strategy());
        strategies.put(StrategyType.HASH_TRUNCATE, new HashTruncateStrategy());
        strategies.put(StrategyType.SEQUENTIAL,    new SequentialStrategy());
    }

    /**
     * Validates params against the strategy's schema, then generates the short code.
     * This is the single entry point — callers never call validate() and generate() separately.
     */
    public String validateAndGenerate(StrategyType type, String url, Long id,
                                       Map<String, Object> rawParams) {
        CodeGenerationStrategy strategy = strategies.getOrDefault(
            type, strategies.get(StrategyType.RANDOM_BASE62));
        Map<String, Object> params = validator.validate(strategy.paramSchema(), rawParams);
        return strategy.generate(url, id, params);
    }

    /** Backward-compatible shim so LinkService compiles until Task 6 updates it. */
    public String generate(StrategyType type, String url, ShortLink entity) {
        Long id = entity != null ? entity.getId() : null;
        return validateAndGenerate(type, url, id, null);
    }

    public List<StrategyParamDefinition> getSchema(StrategyType type) {
        return strategies.get(type).paramSchema();
    }

    public Map<StrategyType, List<StrategyParamDefinition>> getAllSchemas() {
        Map<StrategyType, List<StrategyParamDefinition>> result = new EnumMap<>(StrategyType.class);
        strategies.forEach((type, s) -> result.put(type, s.paramSchema()));
        return result;
    }
}
```

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
