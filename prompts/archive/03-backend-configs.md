# Agent Prompt: Spring Configuration Classes (TASK-09, TASK-10, TASK-11)

## Project Context
You are building an **analytics-driven URL shortener**.
The main application class already exists at:
`backend/src/main/java/com/avivly/urlshortener/UrlShortenerApplication.java`
It has `@EnableAsync` and `@EnableCaching`.

Dependencies available in `pom.xml`: `spring-boot-starter-cache`, `caffeine`, `spring-boot-starter-web`.

## Your Task
Create the three Spring configuration classes: cache, async executor, and CORS.

## Files to Create

### `backend/src/main/java/com/avivly/urlshortener/config/CacheConfig.java`
```java
package com.avivly.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache linksCache = new CaffeineCache("shortLinks",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build());
        return new SimpleCacheManager(List.of(linksCache));
    }
}
```

### `backend/src/main/java/com/avivly/urlshortener/config/AsyncConfig.java`
```java
package com.avivly.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("analytics-");
        executor.initialize();
        return executor;
    }
}
```

### `backend/src/main/java/com/avivly/urlshortener/config/WebMvcConfig.java`
```java
package com.avivly.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:3000", "http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
```

## Acceptance Criteria
- All 3 files compile without errors
- `CacheConfig`: `SimpleCacheManager` is from `org.springframework.cache.support`, cache named `"shortLinks"`, 10-min TTL, max 10,000 entries
- `AsyncConfig`: thread pool prefix is `"analytics-"`, core=4, max=10, queue=500
- `WebMvcConfig`: allows both Vite dev port `5173` and Docker port `3000`
