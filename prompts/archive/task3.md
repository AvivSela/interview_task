# Task 3 — Interface change + all three strategy updates (ATOMIC)

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Strategy classes live in `backend/src/main/java/com/avivly/urlshortener/util/strategy/`.

`ParamType`, `StrategyParamDefinition`, and `StrategyParamValidator` already exist from prior tasks.

## Goal
Change the `CodeGenerationStrategy` interface signature and update all three implementations
(`RandomBase62Strategy`, `HashTruncateStrategy`, `SequentialStrategy`) to match.

**This task must be done atomically** — changing the interface breaks all three impls.
Update all four files in one pass so the project compiles at the end.

After this task the project must compile (`mvn compile -pl backend`).

---

## Current state of files you must change

### `CodeGenerationStrategy.java` (current)
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;

public interface CodeGenerationStrategy {
    String generate(String originalUrl, ShortLink partialEntity);
}
```

### `RandomBase62Strategy.java` (current)
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.util.Base62;

public class RandomBase62Strategy implements CodeGenerationStrategy {

    @Override
    public String generate(String originalUrl, ShortLink partialEntity) {
        return Base62.generate(7);
    }
}
```

### `HashTruncateStrategy.java` (current)
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashTruncateStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    public String generate(String originalUrl, ShortLink partialEntity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(7);
            for (int i = 0; i < 7; i++) {
                int index = (hash[i] & 0xFF) % 62;
                sb.append(CHARS.charAt(index));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
```

### `SequentialStrategy.java` (current)
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.model.ShortLink;

public class SequentialStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    public String generate(String originalUrl, ShortLink partialEntity) {
        Long id = partialEntity.getId();
        if (id == null) {
            throw new IllegalStateException("SequentialStrategy requires a persisted entity");
        }
        return encodeId(id);
    }

    private String encodeId(long id) {
        if (id == 0) return String.valueOf(CHARS.charAt(0));
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            sb.append(CHARS.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
```

---

## New state of all four files

### `CodeGenerationStrategy.java` (replace entirely)
```java
package com.avivly.urlshortener.util.strategy;

import java.util.List;
import java.util.Map;

public interface CodeGenerationStrategy {

    /**
     * @param originalUrl the URL being shortened
     * @param id          the persisted entity ID; null for strategies that don't use it
     * @param params      validated, coerced param map (never null, may be empty)
     */
    String generate(String originalUrl, Long id, Map<String, Object> params);

    List<StrategyParamDefinition> paramSchema();
}
```

### `RandomBase62Strategy.java` (replace entirely)
```java
package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.util.Base62;

import java.util.List;
import java.util.Map;

public class RandomBase62Strategy implements CodeGenerationStrategy {

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
            "Number of characters to generate", 4, 20)
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        int length = (int) params.getOrDefault("length", 7);
        return Base62.generate(length);
    }
}
```

### `HashTruncateStrategy.java` (replace entirely)
```java
package com.avivly.urlshortener.util.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

public class HashTruncateStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
            "Characters to take from hash output", 4, 20),
        StrategyParamDefinition.of("algorithm", ParamType.STRING, false, "SHA-256",
            "Hash algorithm: SHA-256 or SHA-512")
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        String algorithm = (String) params.getOrDefault("algorithm", "SHA-256");
        int length = (int) params.getOrDefault("length", 7);
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int index = (hash[i] & 0xFF) % 62;
                sb.append(CHARS.charAt(index));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(algorithm + " not available", e);
        }
    }
}
```

### `SequentialStrategy.java` (replace entirely)
```java
package com.avivly.urlshortener.util.strategy;

import java.util.List;
import java.util.Map;

public class SequentialStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        StrategyParamDefinition.of("prefix", ParamType.STRING, false, "",
            "Prepended to the encoded ID (e.g. 's-'). Max 16 chars, alphanumeric/hyphen/underscore only")
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        if (id == null) throw new IllegalStateException("SequentialStrategy requires a persisted ID");
        String prefix = (String) params.getOrDefault("prefix", "");
        return prefix + encodeId(id);
    }

    private String encodeId(long id) {
        if (id == 0) return String.valueOf(CHARS.charAt(0));
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            sb.append(CHARS.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
```

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
`StrategyRegistry` will fail to compile after this task — that is expected and will be fixed in Task 4.
Wait — actually StrategyRegistry calls `strategy.generate(url, entity)` which no longer exists.
You must also update `StrategyRegistry.java` minimally so it compiles: change the `generate()` call
to pass `(url, null, Map.of())` as a temporary stub. Task 4 will replace StrategyRegistry fully.

Minimal temporary fix for `StrategyRegistry.generate()`:
```java
public String generate(StrategyType type, String url, com.avivly.urlshortener.model.ShortLink entity) {
    CodeGenerationStrategy strategy = strategies.getOrDefault(type, strategies.get(StrategyType.RANDOM_BASE62));
    Long id = entity != null ? entity.getId() : null;
    return strategy.generate(url, id, java.util.Map.of());
}
```
This stub keeps the existing callers compiling. Task 4 replaces it properly.
