package com.avivly.urlshortener.dto;

import com.avivly.urlshortener.model.GeoStatus;
import org.springframework.lang.Nullable;

public record GeoResult(GeoStatus status, @Nullable String country, @Nullable String city) {

    public static final GeoResult PRIVATE   = new GeoResult(GeoStatus.PRIVATE, null, null);
    public static final GeoResult NOT_FOUND = new GeoResult(GeoStatus.NOT_FOUND, null, null);
    public static final GeoResult ERROR     = new GeoResult(GeoStatus.ERROR, null, null);
    public static final GeoResult DISABLED        = new GeoResult(GeoStatus.DISABLED, null, null);
    public static final GeoResult DATA_INCOMPLETE = new GeoResult(GeoStatus.DATA_INCOMPLETE, null, null);

    public static GeoResult private_()       { return PRIVATE; }
    public static GeoResult notFound()       { return NOT_FOUND; }
    public static GeoResult error()          { return ERROR; }
    public static GeoResult disabled()       { return DISABLED; }
    public static GeoResult dataIncomplete() { return DATA_INCOMPLETE; }

    public static GeoResult resolved(@Nullable String country, @Nullable String city) {
        return new GeoResult(GeoStatus.RESOLVED, country, city);
    }
}
