package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.avivly.urlshortener.util.IpUtils;
import java.io.IOException;
import java.net.InetAddress;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    @GetMapping({"/api/r/{shortCode}", "/{shortCode}"})
    public void redirect(@PathVariable String shortCode,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        ShortLink link = linkService.findByShortCode(shortCode);
        if (link == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!link.isValid()) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", "/link-expired");
            return;
        }
        linkService.recordClick(shortCode);
        analyticsService.logClickAsync(
            shortCode,
            request.getHeader("Referer"),
            request.getHeader("User-Agent"),
            extractClientIp(request)
        );
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", link.getOriginalUrl());
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
                // Strip bracketed IPv6 notation: [2001:db8::1] or [2001:db8::1]:port
                if (candidate.startsWith("[")) {
                    int close = candidate.indexOf(']');
                    if (close > 1) candidate = candidate.substring(1, close);
                }
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
