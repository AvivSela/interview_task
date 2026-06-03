package com.memcyco.urlshortener.dto;

import com.memcyco.urlshortener.model.GeoStatus;
import org.springframework.lang.Nullable;

public record GeoResult(
        GeoStatus status,
        @Nullable String country,
        @Nullable String city
) {
    public static GeoResult private_() {
        return new GeoResult(GeoStatus.PRIVATE, null, null);
    }

    public static GeoResult notFound() {
        return new GeoResult(GeoStatus.NOT_FOUND, null, null);
    }

    public static GeoResult error() {
        return new GeoResult(GeoStatus.ERROR, null, null);
    }

    public static GeoResult disabled() {
        return new GeoResult(GeoStatus.DISABLED, null, null);
    }

    public static GeoResult resolved(@Nullable String country, @Nullable String city) {
        return new GeoResult(GeoStatus.RESOLVED, country, city);
    }
}
