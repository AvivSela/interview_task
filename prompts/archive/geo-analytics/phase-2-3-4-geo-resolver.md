# Phase 2.3 + 2.4 — GeoResolverService + HealthIndicator (parallel)

## Context

Spring Boot URL shortener at `backend/`. Package: `com.avivly.urlshortener`.
Tasks 2.3 and 2.4 are independent new files — run them with two parallel subagents.

**Prerequisites:**
- Phase 2.1 complete: `GeoResult` record exists at `dto/GeoResult.java`
- Phase 2.2 complete: `GeoConfig` bean exists; `DatabaseReader` may be null when path unset
- `GeoLite2-City-Test.mmdb` present at `backend/src/test/resources/`

---

## Spawn two parallel subagents

### Subagent 1 — Task 2.3: `GeoResolverService` (TDD)

#### Step 1 — Write the test first

Create `backend/src/test/java/com/avivly/urlshortener/service/GeoResolverServiceTest.java`:

```java
package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.GeoResult;
import com.avivly.urlshortener.model.GeoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "geo.db.path=src/test/resources/GeoLite2-City-Test.mmdb")
class GeoResolverServiceTest {

    @Autowired
    private GeoResolverService service;

    @Test
    void publicIpResolvesToCountry() {
        GeoResult result = service.resolve("81.2.69.142");
        assertThat(result.status()).isEqualTo(GeoStatus.RESOLVED);
        assertThat(result.country()).isNotBlank();
    }

    @Test
    void privateIpIsPrivate() {
        assertThat(service.resolve("10.0.0.1").status()).isEqualTo(GeoStatus.PRIVATE);
        assertThat(service.resolve("127.0.0.1").status()).isEqualTo(GeoStatus.PRIVATE);
        assertThat(service.resolve("192.168.1.1").status()).isEqualTo(GeoStatus.PRIVATE);
    }

    @Test
    void unknownPublicIpIsNotFound() {
        // 203.0.113.x is TEST-NET-3 (RFC 5737) — not in MaxMind test DB
        GeoResult result = service.resolve("203.0.113.1");
        assertThat(result.status()).isEqualTo(GeoStatus.NOT_FOUND);
    }

    @Test
    void maskHidesLastOctetIPv4() {
        String masked = service.mask("81.2.69.142");
        assertThat(masked).doesNotContain("142");
        assertThat(masked).contains("81.2.69");
    }

    @Test
    void maskHidesLastGroupIPv6() {
        String masked = service.mask("2001:db8::1");
        assertThat(masked).doesNotContain("1").startsWith("2001");
    }
}
```

Run `cd backend && ./mvnw test -pl . -Dtest=GeoResolverServiceTest` — expect compilation
failure (class does not exist yet). That is the expected red state.

#### Step 2 — Implement `GeoResolverService`

Create `backend/src/main/java/com/avivly/urlshortener/service/GeoResolverService.java`:

```java
package com.avivly.urlshortener.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.avivly.urlshortener.dto.GeoResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
@RequiredArgsConstructor
public class GeoResolverService {

    private static final Logger log = LoggerFactory.getLogger(GeoResolverService.class);

    @Nullable
    private final DatabaseReader reader;

    public GeoResult resolve(String ip) {
        if (reader == null) {
            return GeoResult.disabled();
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return GeoResult.private_();
            }
            var response = reader.city(addr);
            Country country = response.getCountry();
            City city = response.getCity();
            return GeoResult.resolved(
                    country.getName(),
                    city.getName()
            );
        } catch (AddressNotFoundException e) {
            return GeoResult.notFound();
        } catch (Exception e) {
            log.warn("Geo lookup failed for {}: {}", mask(ip), e.getMessage());
            return GeoResult.error();
        }
    }

    /** Returns the IP with the last segment replaced by 'x' for safe logging. */
    public String mask(String ip) {
        if (ip == null) return "null";
        if (ip.contains(":")) {
            // IPv6 — mask last colon-separated group
            int last = ip.lastIndexOf(':');
            return ip.substring(0, last + 1) + "x";
        }
        // IPv4 — mask last dot-separated octet
        int last = ip.lastIndexOf('.');
        if (last < 0) return "x";
        return ip.substring(0, last + 1) + "x";
    }
}
```

#### Step 3 — Verify

```bash
cd backend && ./mvnw test -Dtest=GeoResolverServiceTest
```

All five test cases must be green.

---

### Subagent 2 — Task 2.4: `GeoResolverHealthIndicator`

#### Step 1 — Create the health indicator

Create `backend/src/main/java/com/avivly/urlshortener/config/GeoResolverHealthIndicator.java`:

```java
package com.avivly.urlshortener.config;

import com.maxmind.geoip2.DatabaseReader;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class GeoResolverHealthIndicator implements HealthIndicator {

    @Nullable
    private final DatabaseReader reader;

    public GeoResolverHealthIndicator(@Nullable DatabaseReader reader) {
        this.reader = reader;
    }

    @Override
    public Health health() {
        if (reader == null) {
            return Health.status("DEGRADED")
                    .withDetail("reason", "MaxMind DB not configured — geo resolution disabled")
                    .build();
        }
        return Health.up().build();
    }
}
```

#### Step 2 — Expose the health endpoint

Add to `backend/src/main/resources/application.yml` under `management:`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

#### Step 3 — Verify

- With `GEO_DB_PATH` pointing at the test fixture:
  `GET /actuator/health` → `geoResolver.status = UP`
- With `GEO_DB_PATH` unset:
  `GET /actuator/health` → `geoResolver.status = DEGRADED` with detail reason

```bash
cd backend && ./mvnw test
```

---

## After both subagents finish

```bash
cd backend && ./mvnw test
```

Full test suite must stay green.
Commit: `feat: add GeoResolverService and health indicator (Phase 2.3-2.4)`
