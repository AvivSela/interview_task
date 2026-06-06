package com.avivly.urlshortener.model;

public enum GeoStatus {
    RESOLVED,        // country/city successfully populated
    PRIVATE,         // RFC-1918 / loopback address — no lookup performed
    NOT_FOUND,       // public IP not present in the MaxMind DB
    ERROR,           // lookup attempted but threw an unexpected exception
    DISABLED,        // geo database not configured — no lookup attempted
    DATA_INCOMPLETE  // IP found in MaxMind DB but country/city data absent
}
