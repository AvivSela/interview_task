package com.memcyco.urlshortener.config;

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
