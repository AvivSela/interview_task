# Task 1 — New core types: ParamType + StrategyParamDefinition

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
All strategy classes live in `backend/src/main/java/com/avivly/urlshortener/util/strategy/`.

## Goal
Create two new files that everything else in this feature depends on.
After this task the project must compile (`mvn compile -pl backend`).

---

## File 1 — Create `ParamType.java`

**Path:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/ParamType.java`

```java
package com.avivly.urlshortener.util.strategy;

public enum ParamType {
    STRING,
    INTEGER,
    BOOLEAN
}
```

---

## File 2 — Create `StrategyParamDefinition.java`

**Path:** `backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyParamDefinition.java`

```java
package com.avivly.urlshortener.util.strategy;

public record StrategyParamDefinition(
    String    name,
    ParamType type,
    boolean   required,
    String    defaultValue,
    String    description,
    Integer   min,
    Integer   max
) {
    public static StrategyParamDefinition of(String name, ParamType type,
                                              boolean required, String defaultValue,
                                              String description) {
        return new StrategyParamDefinition(name, type, required, defaultValue, description, null, null);
    }
}
```

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
