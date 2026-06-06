# Fix 6 — Extract `IpUtils.isPrivateAddress` utility

## Context

**Prerequisites:** Fixes #10, #8, #7 applied.

Two classes independently encode the same private-IP predicate:

- `RedirectController.extractClientIp()` at line 55:
  `!addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()`
- `GeoResolverService.resolve()` at line 29:
  `addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()`

If one is extended (e.g., to cover carrier-grade NAT `100.64.0.0/10`, which `isSiteLocalAddress` misses), the other silently diverges.

## Objective

Extract the predicate into a package-private `IpUtils` utility class and update both callers. No behaviour change.

## Implementation

### 1. New file: `IpUtils.java`

Create `backend/src/main/java/com/avivly/urlshortener/util/IpUtils.java`:

```java
package com.avivly.urlshortener.util;

import java.net.InetAddress;

public final class IpUtils {
    private IpUtils() {}

    public static boolean isPrivateAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress();
    }
}
```

### 2. `GeoResolverService.java`

Add import:
```java
import com.avivly.urlshortener.util.IpUtils;
```

Replace the inline check in `resolve()`:
```java
// Before:
if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {

// After:
if (IpUtils.isPrivateAddress(addr)) {
```

### 3. `RedirectController.java`

Add import:
```java
import com.avivly.urlshortener.util.IpUtils;
```

Replace the inline check in `extractClientIp()`:
```java
// Before:
if (!addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {

// After:
if (!IpUtils.isPrivateAddress(addr)) {
```

## Verify

```bash
cd backend && ./mvnw compile
```

Compilation must succeed with no errors.

## Commit

`fix: extract IpUtils.isPrivateAddress to prevent predicate drift (#6)`
