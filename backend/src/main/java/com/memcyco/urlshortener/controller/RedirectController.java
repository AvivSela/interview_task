package com.memcyco.urlshortener.controller;

import com.memcyco.urlshortener.model.ShortLink;
import com.memcyco.urlshortener.service.AnalyticsService;
import com.memcyco.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.memcyco.urlshortener.util.IpUtils;
import java.net.InetAddress;
import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    @GetMapping({"/api/r/{shortCode}", "/{shortCode}"})
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        ShortLink link = linkService.findByShortCode(shortCode);
        if (link == null || !link.isValid()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/link-expired"))
                    .build();
        }
        linkService.recordClick(shortCode);
        analyticsService.logClickAsync(
            shortCode,
            request.getHeader("Referer"),
            request.getHeader("User-Agent"),
            extractClientIp(request)
        );
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(link.getOriginalUrl()))
            .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                try {
                    InetAddress addr = InetAddress.getByName(candidate);
                    if (!IpUtils.isPrivateAddress(addr)) {
                        return candidate;
                    }
                } catch (Exception ignored) {}
            }
        }
        return request.getRemoteAddr();
    }
}
