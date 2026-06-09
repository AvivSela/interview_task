package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.dto.AnalyticsResponse;
import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.LinkResponse;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getPrincipal() instanceof Long userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    @PostMapping("/guest")
    public ResponseEntity<LinkResponse> createGuest(@Valid @RequestBody CreateLinkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(LinkResponse.from(linkService.createGuest(req)));
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(LinkResponse.from(linkService.create(req, currentUserId())));
    }

    @GetMapping
    public List<LinkResponse> getAll() {
        return linkService.findAllByOwner(currentUserId()).stream().map(LinkResponse::from).toList();
    }

    @PutMapping("/{id}")
    public LinkResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLinkRequest req) {
        return LinkResponse.from(linkService.update(id, req, currentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        linkService.delete(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        Long callerId = currentUserId();
        var link = linkService.findByShortCode(shortCode);
        if (link == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (link.getOwner() != null && !link.getOwner().getId().equals(callerId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return analyticsService.getAnalytics(shortCode);
    }
}
