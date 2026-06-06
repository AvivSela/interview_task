# Task 5b — New StrategyController

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Controllers live in `backend/src/main/java/com/avivly/urlshortener/controller/`.
This task is independent of Task 5a and can be done in parallel with it.

`StrategyRegistry` already exists and has `getAllSchemas()` which returns
`Map<StrategyType, List<StrategyParamDefinition>>`.

## Goal
Create `StrategyController.java` which exposes `GET /api/strategies`.
The response strips internal fields (`defaultValue`, `min`, `max`) — only `name`, `type`,
`required`, and `description` are returned publicly.

After this task the project must compile (`mvn compile -pl backend`).

---

## File to create

**Path:** `backend/src/main/java/com/avivly/urlshortener/controller/StrategyController.java`

```java
package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.util.strategy.StrategyParamDefinition;
import com.avivly.urlshortener.util.strategy.StrategyRegistry;
import com.avivly.urlshortener.util.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyRegistry strategyRegistry;

    record StrategyParamView(String name, String type, boolean required, String description) {
        static StrategyParamView from(StrategyParamDefinition d) {
            return new StrategyParamView(
                d.name(), d.type().name().toLowerCase(), d.required(), d.description());
        }
    }

    @GetMapping
    public Map<String, List<StrategyParamView>> getAll() {
        Map<String, List<StrategyParamView>> result = new LinkedHashMap<>();
        strategyRegistry.getAllSchemas().forEach((type, defs) ->
            result.put(type.name(), defs.stream().map(StrategyParamView::from).toList()));
        return result;
    }
}
```

---

## Expected response shape
```json
{
  "RANDOM_BASE62": [
    { "name": "length",    "type": "integer", "required": false,
      "description": "Number of characters to generate" }
  ],
  "HASH_TRUNCATE": [
    { "name": "length",    "type": "integer", "required": false,
      "description": "Characters to take from hash output" },
    { "name": "algorithm", "type": "string",  "required": false,
      "description": "Hash algorithm: SHA-256 or SHA-512" }
  ],
  "SEQUENTIAL": [
    { "name": "prefix", "type": "string", "required": false,
      "description": "Prepended to the encoded ID (e.g. 's-'). Max 16 chars, alphanumeric/hyphen/underscore only" }
  ]
}
```
Note: `defaultValue`, `min`, `max` must NOT appear in the response.

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
