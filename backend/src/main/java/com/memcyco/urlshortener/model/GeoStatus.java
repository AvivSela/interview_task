package com.memcyco.urlshortener.model;

public enum GeoStatus {
    PENDING,     // not yet resolved (default at insert time)
    RESOLVED,    // country/city successfully populated
    PRIVATE,     // RFC-1918 / loopback address — no lookup performed
    NOT_FOUND,   // public IP not present in the MaxMind DB
    ERROR,       // lookup attempted but threw an unexpected exception
    DISABLED     // geo database not configured — no lookup attempted
}
