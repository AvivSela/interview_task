# Task 5a — Add strategyParams to CreateLinkRequest

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
This task is independent of Task 5b and can be done in parallel with it.

## Goal
Add `Map<String, Object> strategyParams` to the `CreateLinkRequest` record.
After this task the project must compile (`mvn compile -pl backend`).

---

## Current state of `CreateLinkRequest.java`
```java
package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
```

---

## New state (replace entirely)

**Path:** `backend/src/main/java/com/avivly/urlshortener/dto/CreateLinkRequest.java`

```java
package com.avivly.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Map<String, Object> strategyParams,
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
```

---

## Impact on existing callers
`CreateLinkRequest` is a record — adding a new field changes the canonical constructor.
Find all `new CreateLinkRequest(...)` call sites (they are in test files) and add `null` as
the fourth argument (the new `strategyParams` position) to each one.

Search for `new CreateLinkRequest(` in the test directory:
`backend/src/test/java/com/avivly/urlshortener/LinkControllerIntegrationTest.java`

Each call currently passes 6 args; it must now pass 7, with `null` as the 4th arg.
Example — current:
```java
new CreateLinkRequest("https://example.com/ctrl-create", null, null, null, null, null)
```
After:
```java
new CreateLinkRequest("https://example.com/ctrl-create", null, null, null, null, null, null)
```

Update every such call site in the test file.

---

## Done condition
`mvn compile -pl backend` exits 0 with no errors.
