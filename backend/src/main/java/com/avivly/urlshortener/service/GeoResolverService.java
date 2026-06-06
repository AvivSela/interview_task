package com.avivly.urlshortener.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.avivly.urlshortener.dto.GeoResult;
import com.avivly.urlshortener.util.IpUtils;
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
            if (IpUtils.isPrivateAddress(addr)) {
                return GeoResult.private_();
            }
            var response = reader.city(addr);
            String countryName = response.getCountry().getName();
            if (countryName == null) {
                return GeoResult.dataIncomplete();
            }
            return GeoResult.resolved(countryName, response.getCity().getName());
        } catch (AddressNotFoundException e) {
            return GeoResult.notFound();
        } catch (Exception e) {
            log.warn("Geo lookup failed for {}: {}", mask(ip), e.getMessage());
            return GeoResult.error();
        }
    }

    public boolean isEnabled() {
        return reader != null;
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
