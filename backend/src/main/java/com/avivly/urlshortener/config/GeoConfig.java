package com.avivly.urlshortener.config;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;

@Configuration
public class GeoConfig {

    private static final Logger log = LoggerFactory.getLogger(GeoConfig.class);

    @Bean
    @Nullable
    public DatabaseReader geoDbReader(@Value("${geo.db.path:}") String path) {
        if (path == null || path.isBlank()) {
            log.warn("geo.db.path is not set — geo resolution disabled");
            return null;
        }
        File file = new File(path);
        if (file.isDirectory()) {
            log.warn("geo.db.path points to a directory, not a file: {} — geo resolution disabled (Docker created a directory at this mount point?)", path);
            return null;
        }
        if (!file.exists()) {
            log.warn("MaxMind DB not found at {} — geo resolution disabled", path);
            return null;
        }
        try {
            return new DatabaseReader.Builder(file).withCache(new CHMCache()).build();
        } catch (IOException e) {
            log.warn("Failed to open MaxMind DB at {} — geo resolution disabled: {}", path, e.getMessage());
            return null;
        }
    }
}
