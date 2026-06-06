# Fix 10 — Intern `GeoResult` failure singletons

## Context

`backend/src/main/java/com/avivly/urlshortener/dto/GeoResult.java`

`GeoResult` is a record with five factory methods. The four failure factories (`disabled`, `private_`, `notFound`, `error`) each call `new GeoResult(...)` on every invocation. At high click volume with geo disabled, `GeoResult.disabled()` is called on every redirect, generating short-lived garbage objects on the async executor threads.

This is the **first fix to apply** — fixes #8 and #7 (which add/remove enum values and add a new constant) depend on `GeoResult` already having the new shape.

## Objective

Replace the four allocating failure factories with pre-allocated `static final` constants. The public factory method names stay identical so no callers need updating. Add a `DATA_INCOMPLETE` constant and factory now (fix #8 will add the `GeoStatus` value; the constant is a forward reference that must compile clean after #8 is applied — do NOT add it here).

## Implementation

Edit `backend/src/main/java/com/avivly/urlshortener/dto/GeoResult.java`.

Replace the entire file with:

```java
package com.avivly.urlshortener.dto;

import com.avivly.urlshortener.model.GeoStatus;
import org.springframework.lang.Nullable;

public record GeoResult(GeoStatus status, @Nullable String country, @Nullable String city) {

    public static final GeoResult PRIVATE   = new GeoResult(GeoStatus.PRIVATE, null, null);
    public static final GeoResult NOT_FOUND = new GeoResult(GeoStatus.NOT_FOUND, null, null);
    public static final GeoResult ERROR     = new GeoResult(GeoStatus.ERROR, null, null);
    public static final GeoResult DISABLED  = new GeoResult(GeoStatus.DISABLED, null, null);

    public static GeoResult private_()  { return PRIVATE; }
    public static GeoResult notFound()  { return NOT_FOUND; }
    public static GeoResult error()     { return ERROR; }
    public static GeoResult disabled()  { return DISABLED; }

    public static GeoResult resolved(@Nullable String country, @Nullable String city) {
        return new GeoResult(GeoStatus.RESOLVED, country, city);
    }
}
```

> **Note:** `DATA_INCOMPLETE` constant and factory are added in fix #8 once `GeoStatus.DATA_INCOMPLETE` exists. Do NOT add them here.

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: intern GeoResult failure singletons as static constants (#10)`
