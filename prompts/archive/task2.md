# Task 2 — New component: StrategyParamValidator

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Strategy classes live in `backend/src/main/java/com/avivly/urlshortener/util/strategy/`.

`ParamType` and `StrategyParamDefinition` already exist from a prior task.
Do NOT modify those files.

## Goal
Create `StrategyParamValidator.java` — a `@Component` that validates and coerces raw strategy
params against a declared schema. This is the single place where all validation logic lives.
After this task the project must compile (`mvn compile -pl backend`).

---

## File to create

**Path:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyParamValidator.java`

```java
package com.avivly.urlshortener.util.strategy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StrategyParamValidator {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("SHA-256", "SHA-512");

    /**
     * Validates rawParams against schema.
     * Returns a clean, coerced Map<String, Object> ready for the strategy.
     * Throws ResponseStatusException(400) on any violation.
     */
    public Map<String, Object> validate(List<StrategyParamDefinition> schema,
                                        Map<String, Object> rawParams) {
        Map<String, Object> params = rawParams == null ? Map.of() : rawParams;
        Map<String, StrategyParamDefinition> byName = schema.stream()
            .collect(Collectors.toMap(StrategyParamDefinition::name, d -> d));

        for (String key : params.keySet()) {
            if (!byName.containsKey(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown strategy parameter: " + key);
            }
        }

        Map<String, Object> coerced = new HashMap<>();
        for (StrategyParamDefinition def : schema) {
            Object raw = params.getOrDefault(def.name(),
                def.defaultValue() != null ? def.defaultValue() : null);

            if (raw == null) {
                if (def.required()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Required strategy parameter missing: " + def.name());
                }
                continue;
            }

            coerced.put(def.name(), coerce(def, raw));
        }
        return coerced;
    }

    private Object coerce(StrategyParamDefinition def, Object raw) {
        return switch (def.type()) {
            case INTEGER -> {
                int value;
                try {
                    value = Integer.parseInt(raw.toString());
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be an integer");
                }
                if (def.min() != null && value < def.min()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be >= " + def.min());
                }
                if (def.max() != null && value > def.max()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be <= " + def.max());
                }
                yield value;
            }
            case STRING -> {
                String value = raw.toString();
                if ("algorithm".equals(def.name()) && !ALLOWED_ALGORITHMS.contains(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported algorithm '" + value + "'. Allowed: " + ALLOWED_ALGORITHMS);
                }
                if ("prefix".equals(def.name()) && !value.matches("[A-Za-z0-9_\\-]{0,16}")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter 'prefix' must match [A-Za-z0-9_-]{0,16}");
                }
                yield value;
            }
            case BOOLEAN -> {
                String s = raw.toString().toLowerCase();
                if (!s.equals("true") && !s.equals("false")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be true or false");
                }
                yield Boolean.parseBoolean(s);
            }
        };
    }
}
```

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
